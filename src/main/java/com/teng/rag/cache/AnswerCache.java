package com.teng.rag.cache;

import java.util.Optional;

/**
 * 问答缓存抽象。
 *
 * <p>抽接口的两个理由：</p>
 * <ol>
 *   <li>生产走 Redis（跨进程共享），单测用内存实现（毫秒级、无外部依赖）</li>
 *   <li>Redis 故障时可整体降级为"永不命中"，问答链路照常工作——缓存永远是加速器不是依赖</li>
 * </ol>
 */
public interface AnswerCache {

    /** 命中返回完整条目；未命中/Redis 异常/解析失败一律 empty（当作未命中） */
    Optional<AnswerCachePolicy.CachedAnswer> get(String question);

    /** 写入正缓存（TTL 带随机抖动） */
    void put(String question, AnswerCachePolicy.CachedAnswer answer);

    /** 写入负缓存（短 TTL，防空穿透） */
    void putNegative(String question, String message);

    /**
     * 知识库变更（文档增删）时调用：使所有存量缓存失效。
     * 实现为版本号自增而非全量删除——旧键等 TTL 自然过期。
     */
    void invalidateAll();
}
