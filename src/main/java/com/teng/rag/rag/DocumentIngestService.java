package com.teng.rag.rag;

import com.teng.rag.config.RagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 文档摄取服务（Indexing 阶段）：
 * 上传 → 解析(txt/md/pdf) → 切分 → 批量嵌入 → 写入向量库 → 登记元数据。
 *
 * <p>刻意手写每一步而不用 EmbeddingStoreIngestor 一条龙：
 * 需要拿到每个向量块的 ID（删除文档时精准清理），也让面试官能逐段追问。</p>
 */
@Service
public class DocumentIngestService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final TextChunker chunker;
    private final DocumentRegistry registry;
    private final RagProperties props;

    public DocumentIngestService(EmbeddingModel embeddingModel,
                                 EmbeddingStore<TextSegment> embeddingStore,
                                 TextChunker chunker,
                                 DocumentRegistry registry,
                                 RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chunker = chunker;
        this.registry = registry;
        this.props = props;
    }

    public DocumentMeta ingest(MultipartFile file) throws IOException {
        String docId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();

        Document document = parse(file, docId, originalName);
        List<TextSegment> segments = chunker.split(document, docId);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("文档解析后为空，无法入库: " + originalName);
        }

        // 批量嵌入（BGE 本地 ONNX 推理）：embedAll 返回 Response<List<Embedding>>
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 逐块写入并记录向量 ID，支持后续按文档精确删除
        List<String> vectorIds = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            String vectorId = embeddingStore.add(embeddings.get(i), segments.get(i));
            vectorIds.add(vectorId);
        }

        DocumentMeta meta = DocumentRegistry.create(docId, originalName, file.getSize(), segments.size(), vectorIds);
        registry.save(meta);
        return meta;
    }

    private Document parse(MultipartFile file, String docId, String originalName) throws IOException {
        String lower = originalName.toLowerCase(Locale.ROOT);
        // 落到临时文件后复用官方 DocumentLoader（按扩展名选解析器）
        Path temp = Files.createTempFile("rag-upload-", "-" + originalName);
        try {
            file.transferTo(temp);
            if (lower.endsWith(".pdf")) {
                var document = FileSystemDocumentLoader.loadDocument(temp, new ApachePdfBoxDocumentParser());
                document.metadata().put("file_name", originalName);
                document.metadata().put("doc_id", docId);
                return document;
            }
            if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
                var document = FileSystemDocumentLoader.loadDocument(temp, new TextDocumentParser());
                document.metadata().put("file_name", originalName);
                document.metadata().put("doc_id", docId);
                return document;
            }
            throw new IllegalArgumentException("暂只支持 .txt / .md / .pdf: " + originalName);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** 按文档删除：先从向量库批量移除（按存好的向量 ID），再清元数据。 */
    public int remove(String docId) {
        DocumentMeta meta = registry.remove(docId).orElse(null);
        if (meta == null) {
            return 0;
        }
        embeddingStore.removeAll(meta.vectorIds());
        return meta.chunkCount();
    }

    public RagProperties properties() {
        return props;
    }
}
