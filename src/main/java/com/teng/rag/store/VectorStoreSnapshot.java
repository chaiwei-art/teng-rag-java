package com.teng.rag.store;

import com.teng.rag.config.RagProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 向量库快照：把内存 EmbeddingStore 序列化到本地 JSON 文件。
 *
 * <p>解决的面试级问题——<b>元数据持久（MySQL）与向量内存的重启一致性</b>：
 * 只有 doc_meta 落库而向量不落，重启后"文档列表有、检索却为空"。
 * 方案：向量库用官方 {@code serializeToFile} 落快照，启动时 {@code fromFile} 恢复，
 * 摄取/删除后增量重写。快照与 doc_meta 不一致时由启动对账器兜底（见 SampleDataInitializer）。</p>
 */
@Component
public class VectorStoreSnapshot {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreSnapshot.class);

    private final InMemoryEmbeddingStore<TextSegment> store;
    private final Path file;

    public VectorStoreSnapshot(InMemoryEmbeddingStore<TextSegment> store, RagProperties props) {
        this.store = store;
        this.file = Path.of(props.store().file());
    }

    /** 摄取/删除后调用：全量重写快照（演示规模下 O(N) 可接受）。 */
    public void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            store.serializeToFile(file);
        } catch (Exception e) {
            // 快照失败不影响本次请求，只损失"重启后免重建"
            log.warn("[vector] 快照写入失败: {}", e.getMessage());
        }
    }

    public Path file() {
        return file;
    }
}
