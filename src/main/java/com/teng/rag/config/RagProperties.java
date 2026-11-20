package com.teng.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置属性：application.yml 中 rag.* 前缀。
 * 全部支持环境变量覆盖，LLM 网关默认指向本机已有的 OpenAI 兼容网关。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(Llm llm, Retrieval retrieval, Chunk chunk, Cache cache, Store store) {

    public record Llm(String baseUrl, String apiKey, String model, long timeoutSeconds) {
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Retrieval(int topK, double minScore) {
    }

    public record Chunk(int maxSegmentSize, int maxOverlapSize) {
    }

    /**
     * 问答缓存策略：
     * <ul>
     *   <li>正缓存 TTL + 随机抖动 → 防雪崩（大量 key 同时过期）</li>
     *   <li>负缓存短 TTL → 防穿透（不存在的答案不许缓存太久）</li>
     * </ul>
     */
    public record Cache(long ttlSeconds, long jitterSeconds, long negativeTtlSeconds) {
    }

    /**
     * 向量库快照文件：内存 EmbeddingStore 的 JSON 持久化位置。
     * 解决"元数据在 MySQL、向量在内存"的重启不一致问题（启动时加载快照）。
     */
    public record Store(String file) {
    }
}
