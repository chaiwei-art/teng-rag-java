package com.teng.rag.rag;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档元数据注册表（内存实现）。
 *
 * <p>与向量库解耦：EmbeddingStore 只存「向量+文本」，文档名/时间/向量ID 等业务信息放这里。
 * 两个存储各自独立演进——这是 RAG 系统里常见的职责拆分（元数据 vs 向量）。</p>
 *
 * <p>当前用 ConcurrentHashMap；换成 MySQL/MyBatis-Plus 时只需实现同名方法，
 * Controller 层无感知。</p>
 */
@Component
public class DocumentRegistry {

    private final Map<String, DocumentMeta> store = new ConcurrentHashMap<>();

    public void save(DocumentMeta meta) {
        store.put(meta.id(), meta);
    }

    public Optional<DocumentMeta> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<DocumentMeta> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(DocumentMeta::createdAt).reversed())
                .toList();
    }

    public Optional<DocumentMeta> remove(String id) {
        return Optional.ofNullable(store.remove(id));
    }

    public int count() {
        return store.size();
    }

    public static DocumentMeta create(String id, String name, long sizeBytes, int chunkCount, List<String> vectorIds) {
        return new DocumentMeta(id, name, sizeBytes, chunkCount, Instant.now(), vectorIds);
    }
}
