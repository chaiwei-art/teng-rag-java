package com.teng.rag.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存策略测试：键稳定性 / 版本隔离 / TTL 抖动边界。
 */
class AnswerCachePolicyTest {

    private final AnswerCachePolicy policy = new AnswerCachePolicy(1800, 300, 30);

    @Test
    void should_build_stable_key_per_question_and_version() {
        String k1 = policy.positiveKey(0, "退货政策是什么");
        String k2 = policy.positiveKey(0, "退货政策是什么");
        String k3 = policy.positiveKey(1, "退货政策是什么"); // 版本变 → 键变（缓存失效）
        String k4 = policy.positiveKey(0, "会员怎么升级");

        assertThat(k1).isEqualTo(k2);
        assertThat(k1).isNotEqualTo(k3);
        assertThat(k1).isNotEqualTo(k4);
    }

    @Test
    void should_namespace_positive_and_negative_keys_apart() {
        String positive = policy.positiveKey(0, "问题");
        String negative = policy.negativeKey(0, "问题");
        assertThat(positive).isNotEqualTo(negative);
        assertThat(positive).startsWith("rag:a:");
        assertThat(negative).startsWith("rag:n:");
    }

    @Test
    void should_sha256_hash_question_into_64_hex_chars() {
        String hash = AnswerCachePolicy.sha256("中文问题 with spaces");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(AnswerCachePolicy.sha256("中文问题 with spaces")).isEqualTo(hash);
        assertThat(AnswerCachePolicy.sha256("别的问题")).isNotEqualTo(hash);
    }

    @Test
    void should_jitter_positive_ttl_within_bounds() {
        // 抖动必须落在 [ttl, ttl+jitter]：下界保证不提前过期，上界保证有限过期
        for (int i = 0; i < 200; i++) {
            long ttl = policy.positiveTtlSeconds();
            assertThat(ttl).isBetween(1800L, 2100L);
        }
    }

    @Test
    void should_use_configured_negative_ttl() {
        assertThat(policy.negativeTtlSeconds()).isEqualTo(30);
        assertThat(new AnswerCachePolicy(60, 0, 5).positiveTtlSeconds()).isEqualTo(60); // jitter=0
    }
}
