package com.teng.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置属性：application.yml 中 rag.* 前缀。
 * 全部支持环境变量覆盖，LLM 网关默认指向本机已有的 OpenAI 兼容网关。
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(Llm llm, Retrieval retrieval, Chunk chunk) {

    public record Llm(String baseUrl, String apiKey, String model, long timeoutSeconds) {
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Retrieval(int topK, double minScore) {
    }

    public record Chunk(int maxSegmentSize, int maxOverlapSize) {
    }
}
