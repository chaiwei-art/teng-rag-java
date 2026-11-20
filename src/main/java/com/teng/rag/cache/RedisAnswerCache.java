package com.teng.rag.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teng.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 问答缓存（生产实现）。
 *
 * <p>键设计见 {@link AnswerCachePolicy}：版本号 + SHA-256，TTL 带抖动。</p>
 *
 * <p><b>降级策略（面试高频）</b>：所有 Redis 操作包在 try/catch 里——
 * Redis 挂了只记一条 warn，返回 empty（等价未命中），问答链路直连 LLM 照常工作。
 * 缓存是加速器，绝不能成为可用性单点。</p>
 */
@Component
public class RedisAnswerCache implements AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(RedisAnswerCache.class);

    private static final String VERSION_KEY = "rag:cachever";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AnswerCachePolicy policy;

    public RedisAnswerCache(StringRedisTemplate redis, ObjectMapper objectMapper, RagProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.policy = new AnswerCachePolicy(props.cache());
    }

    @Override
    public Optional<AnswerCachePolicy.CachedAnswer> get(String question) {
        try {
            int version = version();
            String raw = redis.opsForValue().get(policy.positiveKey(version, question));
            if (raw == null) {
                raw = redis.opsForValue().get(policy.negativeKey(version, question));
            }
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, AnswerCachePolicy.CachedAnswer.class));
        } catch (Exception e) {
            log.warn("[cache] 读取失败，降级为未命中: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String question, AnswerCachePolicy.CachedAnswer answer) {
        try {
            String json = objectMapper.writeValueAsString(answer);
            redis.opsForValue().set(
                    policy.positiveKey(version(), question),
                    json,
                    Duration.ofSeconds(policy.positiveTtlSeconds()));
        } catch (Exception e) {
            log.warn("[cache] 写入失败（跳过缓存）: {}", e.getMessage());
        }
    }

    @Override
    public void putNegative(String question, String message) {
        try {
            String json = objectMapper.writeValueAsString(
                    new AnswerCachePolicy.CachedAnswer(message, java.util.List.of()));
            redis.opsForValue().set(
                    policy.negativeKey(version(), question),
                    json,
                    Duration.ofSeconds(policy.negativeTtlSeconds()));
        } catch (Exception e) {
            log.warn("[cache] 负缓存写入失败（跳过）: {}", e.getMessage());
        }
    }

    @Override
    public void invalidateAll() {
        try {
            // INCR 使版本自增：所有旧键逻辑失效，靠 TTL 自然回收（免 SCAN 全量删除）
            redis.opsForValue().increment(VERSION_KEY);
        } catch (Exception e) {
            log.warn("[cache] 版本自增失败: {}", e.getMessage());
        }
    }

    private int version() {
        String raw = redis.opsForValue().get(VERSION_KEY);
        return raw == null ? 0 : Integer.parseInt(raw);
    }
}
