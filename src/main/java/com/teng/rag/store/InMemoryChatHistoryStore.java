package com.teng.rag.store;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内存实现：单测用（不注册为 Bean，避免与 MySQL 实现争抢注入）。
 */
public class InMemoryChatHistoryStore implements ChatHistoryStore {

    private final ConcurrentHashMap<Long, ChatHistoryEntity> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(0);

    @Override
    public void save(ChatHistoryEntity entity) {
        entity.setId(seq.incrementAndGet());
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        store.put(entity.getId(), entity);
    }

    @Override
    public List<ChatHistoryEntity> findRecent(int limit) {
        return store.values().stream()
                .sorted(Comparator.comparing(ChatHistoryEntity::getCreatedAt).reversed()
                        .thenComparing(ChatHistoryEntity::getId, Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<ChatHistoryEntity> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public long count() {
        return store.size();
    }
}
