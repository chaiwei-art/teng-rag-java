package com.teng.rag.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * LangChain4j 装配层。
 *
 * <p>刻意不用 spring-boot-starter 的自动装配，所有 Bean 手动声明——
 * 面试时每一步都讲得清楚（用什么模型、为什么、怎么换）。</p>
 *
 * <ul>
 *   <li>嵌入：BGE-small-zh-v1.5（jar 内置 ONNX，离线运行，中文优化）</li>
 *   <li>向量库：内存实现，接口不变可平移 Milvus/PgVector</li>
 *   <li>LLM：OpenAI 兼容网关 + 流式输出</li>
 * </ul>
 */
@Configuration
public class RagConfig {

    /**
     * 中文文本嵌入模型（ONNX 本地推理）。
     * 首次调用会从 classpath 加载 ~95MB 模型到内存，之后纯本地、零 API 成本。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel();
    }

    /**
     * 内存向量库。
     * MVP 选它：零外部依赖、进程重启即失。
     * 生产替换路径：换 Bean 实现（Milvus/PgVector/Elasticsearch），
     * 业务代码只依赖 EmbeddingStore 接口，不感知具体存储。
     */
    @Bean
    public InMemoryEmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    /**
     * OpenAI 兼容流式聊天模型（同步/异步回调式流）。
     */
    @Bean
    public OpenAiStreamingChatModel streamingChatModel(RagProperties props) {
        RagProperties.Llm llm = props.llm();
        if (!llm.hasKey()) {
            // 启动不失败：没 Key 时健康检查可过，调用接口给出明确报错
            return OpenAiStreamingChatModel.builder()
                    .baseUrl(llm.baseUrl())
                    .apiKey("missing-api-key")
                    .modelName(llm.model())
                    .timeout(Duration.ofSeconds(llm.timeoutSeconds()))
                    .build();
        }
        return OpenAiStreamingChatModel.builder()
                .baseUrl(llm.baseUrl())
                .apiKey(llm.apiKey())
                .modelName(llm.model())
                .timeout(Duration.ofSeconds(llm.timeoutSeconds()))
                .build();
    }
}
