package com.teng.rag.store;

import java.util.List;
import java.util.Optional;

/**
 * 文档元数据存储抽象。
 *
 * <p>为什么抽象：元数据（MySQL）与向量（内存 EmbeddingStore）是两种存储，
 * 各自独立演进；测试用内存实现毫秒级跑完，生产用 MyBatis-Plus 走 MySQL。
 * 这也是面试高频题——"元数据和向量怎么分工"。</p>
 */
public interface DocumentMetaStore {

    void save(DocMetaEntity meta);

    Optional<DocMetaEntity> findById(String id);

    /** 按创建时间倒序 */
    List<DocMetaEntity> findAll();

    Optional<DocMetaEntity> remove(String id);

    int count();

    /** 清空全部元数据（启动对账用：向量快照缺失时清理不可恢复的陈旧行） */
    void clear();
}
