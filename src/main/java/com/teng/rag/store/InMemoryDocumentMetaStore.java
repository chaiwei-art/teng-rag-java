package com.teng.rag.store;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现：单测与无 MySQL 场景使用。
 * 与生产实现行为契约完全一致（同一套测试验证两者）。
 */
public class InMemoryDocumentMetaStore implements DocumentMetaStore {

    private final Map<String, DocMetaEntity> store = new ConcurrentHashMap<>();

    @Override
    public void save(DocMetaEntity meta) {
        if (meta.getCreatedAt() == null) {
            meta.setCreatedAt(LocalDateTime.now());
        }
        store.put(meta.getId(), meta);
    }

    @Override
    public Optional<DocMetaEntity> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<DocMetaEntity> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(DocMetaEntity::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public Optional<DocMetaEntity> remove(String id) {
        return Optional.ofNullable(store.remove(id));
    }

    @Override
    public int count() {
        return store.size();
    }

    @Override
    public void clear() {
        store.clear();
    }
}
