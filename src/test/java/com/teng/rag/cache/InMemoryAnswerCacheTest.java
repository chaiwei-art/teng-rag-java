package com.teng.rag.cache;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内存缓存实现测试：与 Redis 实现共享同一契约（命中/未命中/负缓存/版本失效）。
 * 负 TTL 用 -1 制造"已过期"，无需 sleep，测试毫秒级完成。
 */
class InMemoryAnswerCacheTest {

    private static AnswerCachePolicy policy(long ttl, long jitter, long negTtl) {
        return new AnswerCachePolicy(ttl, jitter, negTtl);
    }

    @Test
    void should_return_empty_for_unknown_question() {
        InMemoryAnswerCache cache = new InMemoryAnswerCache(policy(1800, 300, 30));
        assertThat(cache.get("从未问过")).isEmpty();
    }

    @Test
    void should_put_and_get_positive_entry_with_sources() {
        InMemoryAnswerCache cache = new InMemoryAnswerCache(policy(1800, 300, 30));
        var sources = List.of(new com.teng.rag.rag.RagQueryService.Hit("手册.txt", "内容", 0.9));
        cache.put("问题", new AnswerCachePolicy.CachedAnswer("答案全文", sources));

        var hit = cache.get("问题").orElseThrow();
        assertThat(hit.answer()).isEqualTo("答案全文");
        assertThat(hit.sources()).hasSize(1);
        assertThat(hit.sources().get(0).docName()).isEqualTo("手册.txt");
    }

    @Test
    void should_serve_negative_entry_then_expire() {
        // 负 TTL = -1 → 写入即过期：验证"负缓存有时限"的逻辑本身
        InMemoryAnswerCache cache = new InMemoryAnswerCache(policy(1800, 300, -1));
        cache.putNegative("查不到", "没有找到");
        assertThat(cache.get("查不到")).isEmpty();

        // 正常 TTL 下负缓存可命中
        InMemoryAnswerCache fresh = new InMemoryAnswerCache(policy(1800, 300, 30));
        fresh.putNegative("查不到", "没有找到");
        var hit = fresh.get("查不到").orElseThrow();
        assertThat(hit.answer()).isEqualTo("没有找到");
        assertThat(hit.sources()).isEmpty();
    }

    @Test
    void should_invalidate_all_entries_on_version_bump() {
        InMemoryAnswerCache cache = new InMemoryAnswerCache(policy(1800, 300, 30));
        cache.put("问题A", new AnswerCachePolicy.CachedAnswer("答A", List.of()));
        cache.putNegative("问题B", "没找到");

        cache.invalidateAll();

        assertThat(cache.get("问题A")).isEmpty();
        assertThat(cache.get("问题B")).isEmpty();
    }

    @Test
    void should_let_positive_entry_shadow_negative_for_same_question() {
        InMemoryAnswerCache cache = new InMemoryAnswerCache(policy(1800, 300, 30));
        cache.putNegative("问题", "旧的没找到");
        cache.put("问题", new AnswerCachePolicy.CachedAnswer("新答案", List.of()));
        assertThat(cache.get("问题").orElseThrow().answer()).isEqualTo("新答案");
    }
}
