package com.teng.rag.cache;

import com.teng.rag.config.RagProperties;
import com.teng.rag.rag.RagQueryService;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存键与 TTL 策略（纯函数，便于单测）。
 *
 * <p>三个设计点对应三道面试题：</p>
 * <ul>
 *   <li><b>键 = 版本号 + 问题哈希</b>：问题原文不直接进键（长度/字符集不可控），
 *       用 SHA-256；版本号在文档增删时 INCR → 缓存与知识库保持最终一致，
 *       旧键靠 TTL 自然过期（避免 KEYS/SCAN 全量删除的 O(N)）</li>
 *   <li><b>正缓存 TTL + 随机抖动</b> → 防雪崩：批量 key 不会在同一时刻集体过期</li>
 *   <li><b>负缓存短 TTL</b> → 防穿透："没查到"只缓存 30 秒，
 *       知识库新增文档后很快能重新回答</li>
 * </ul>
 */
public class AnswerCachePolicy {

    private final long ttlSeconds;
    private final long jitterSeconds;
    private final long negativeTtlSeconds;

    public AnswerCachePolicy(RagProperties.Cache cache) {
        this(cache.ttlSeconds(), cache.jitterSeconds(), cache.negativeTtlSeconds());
    }

    public AnswerCachePolicy(long ttlSeconds, long jitterSeconds, long negativeTtlSeconds) {
        this.ttlSeconds = ttlSeconds;
        this.jitterSeconds = jitterSeconds;
        this.negativeTtlSeconds = negativeTtlSeconds;
    }

    /** 正缓存键：rag:a:{version}:{sha256(问题)} */
    public String positiveKey(int version, String question) {
        return "rag:a:" + version + ":" + sha256(question);
    }

    /** 负缓存键：独立命名空间，避免与正缓存互相覆盖 */
    public String negativeKey(int version, String question) {
        return "rag:n:" + version + ":" + sha256(question);
    }

    /** TTL = 基准 + [0, jitter] 随机抖动 */
    public long positiveTtlSeconds() {
        if (jitterSeconds <= 0) {
            return ttlSeconds;
        }
        return ttlSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
    }

    public long negativeTtlSeconds() {
        return negativeTtlSeconds;
    }

    static String sha256(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 缓存条目：完整回答 + 引用来源（命中时要回放 sources 事件） */
    public record CachedAnswer(String answer, List<RagQueryService.Hit> sources) {
    }
}
