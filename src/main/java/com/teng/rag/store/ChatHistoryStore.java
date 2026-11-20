package com.teng.rag.store;

import java.util.List;
import java.util.Optional;

/**
 * 对话历史存储抽象：MySQL 实现给生产，内存实现给单测。
 * 与 {@link DocumentMetaStore} 同一套"接口 + 双实现"模式。
 */
public interface ChatHistoryStore {

    void save(ChatHistoryEntity entity);

    /** 最近 limit 条，按时间倒序 */
    List<ChatHistoryEntity> findRecent(int limit);

    Optional<ChatHistoryEntity> findById(long id);

    long count();
}
