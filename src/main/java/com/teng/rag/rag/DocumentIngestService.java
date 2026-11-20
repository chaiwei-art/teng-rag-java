package com.teng.rag.rag;

import com.teng.rag.cache.AnswerCache;
import com.teng.rag.config.RagProperties;
import com.teng.rag.store.DocMetaEntity;
import com.teng.rag.store.DocumentMetaStore;
import com.teng.rag.store.VectorStoreSnapshot;
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
 * 上传 → 解析(txt/md/pdf) → 切分 → 批量嵌入 → 写入向量库 → 元数据落 MySQL → 存向量快照。
 *
 * <p>刻意手写每一步而不用 EmbeddingStoreIngestor 一条龙：
 * 需要拿到每个向量块的 ID（删除文档时精准清理），也让面试官能逐段追问。</p>
 *
 * <p>文档增删后的两件联动事：</p>
 * <ul>
 *   <li>{@code answerCache.invalidateAll()}——知识库变了，旧缓存问答可能过时（版本号自增）</li>
 *   <li>{@code snapshot.save()}——向量落快照，重启后元数据与向量保持一致</li>
 * </ul>
 */
@Service
public class DocumentIngestService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final TextChunker chunker;
    private final DocumentMetaStore metaStore;
    private final AnswerCache answerCache;
    private final VectorStoreSnapshot snapshot;
    private final RagProperties props;

    public DocumentIngestService(EmbeddingModel embeddingModel,
                                 EmbeddingStore<TextSegment> embeddingStore,
                                 TextChunker chunker,
                                 DocumentMetaStore metaStore,
                                 AnswerCache answerCache,
                                 VectorStoreSnapshot snapshot,
                                 RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chunker = chunker;
        this.metaStore = metaStore;
        this.answerCache = answerCache;
        this.snapshot = snapshot;
        this.props = props;
    }

    /** 上传摄取 */
    public DocMetaEntity ingest(MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        Path temp = Files.createTempFile("rag-upload-", "-" + originalName);
        try {
            file.transferTo(temp);
            Document document = load(temp, originalName);
            return ingestDocument(document, originalName, file.getSize());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * 纯文本摄取（种子文档/评测数据用）：跳过落盘与 multipart。
     */
    public DocMetaEntity ingestText(String name, String text) {
        Document document = Document.from(text);
        document.metadata().put("file_name", name);
        return ingestDocument(document, name, text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    private DocMetaEntity ingestDocument(Document document, String name, long sizeBytes) {
        String docId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        document.metadata().put("doc_id", docId);

        List<TextSegment> segments = chunker.split(document, docId);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("文档解析后为空，无法入库: " + name);
        }

        // 批量嵌入（BGE 本地 ONNX 推理）：embedAll 返回 Response<List<Embedding>>
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 逐块写入并记录向量 ID，支持后续按文档精确删除
        List<String> vectorIds = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            vectorIds.add(embeddingStore.add(embeddings.get(i), segments.get(i)));
        }

        DocMetaEntity meta = new DocMetaEntity();
        meta.setId(docId);
        meta.setName(name);
        meta.setSizeBytes(sizeBytes);
        meta.setChunkCount(segments.size());
        meta.setVectorIds(String.join(",", vectorIds));
        meta.setCreatedAt(java.time.LocalDateTime.now());
        metaStore.save(meta);

        // 联动：缓存失效（版本自增）+ 向量快照落盘
        answerCache.invalidateAll();
        snapshot.save();
        return meta;
    }

    private Document load(Path file, String originalName) throws IOException {
        String lower = originalName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return FileSystemDocumentLoader.loadDocument(file, new ApachePdfBoxDocumentParser());
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return FileSystemDocumentLoader.loadDocument(file, new TextDocumentParser());
        }
        throw new IllegalArgumentException("暂只支持 .txt / .md / .pdf: " + originalName);
    }

    /** 按文档删除：向量块批量移除 → 元数据清理 → 缓存失效 → 快照重写。 */
    public int remove(String docId) {
        DocMetaEntity meta = metaStore.remove(docId).orElse(null);
        if (meta == null) {
            return 0;
        }
        embeddingStore.removeAll(List.of(meta.getVectorIds().split(",")));
        answerCache.invalidateAll();
        snapshot.save();
        return meta.getChunkCount();
    }

    public RagProperties properties() {
        return props;
    }
}
