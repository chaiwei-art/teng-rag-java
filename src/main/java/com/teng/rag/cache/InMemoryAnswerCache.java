package com.teng.rag.cache;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存实现：单测使用。行为契约与 Redis 实现一致（同一套编排测试验证两者）。
 * 过期用时间戳判断，测试可通过 ttl=0 立即过期，无需 sleep。
 */
public class InMemoryAnswerCache implements AnswerCache {

    private final AnswerCachePolicy policy;
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicInteger version = new AtomicInteger(0);

    public InMemoryAnswerCache(AnswerCachePolicy policy) {
        this.policy = policy;
    }

    private record Entry(AnswerCachePolicy.CachedAnswer value, Instant expireAt) {
    }

    @Override
    public Optional<AnswerCachePolicy.CachedAnswer> get(String question) {
        int v = version.get();
        // 先查正缓存，再查负缓存（负缓存存的是"未找到"文案）
        Optional<AnswerCachePolicy.CachedAnswer> hit = lookup(policy.positiveKey(v, question));
        return hit.isPresent() ? hit : lookup(policy.negativeKey(v, question));
    }

    private Optional<AnswerCachePolicy.CachedAnswer> lookup(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expireAt())) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public void put(String question, AnswerCachePolicy.CachedAnswer answer) {
        store.put(policy.positiveKey(version.get(), question),
                new Entry(answer, Instant.now().plusSeconds(policy.positiveTtlSeconds())));
    }

    @Override
    public void putNegative(String question, String message) {
        store.put(policy.negativeKey(version.get(), question),
                new Entry(new AnswerCachePolicy.CachedAnswer(message, java.util.List.of()),
                        Instant.now().plusSeconds(policy.negativeTtlSeconds())));
    }

    @Override
    public void invalidateAll() {
        // 版本自增即可让存量键全部"逻辑失效"，与 Redis 实现同一策略
        version.incrementAndGet();
        store.clear();
    }
}
