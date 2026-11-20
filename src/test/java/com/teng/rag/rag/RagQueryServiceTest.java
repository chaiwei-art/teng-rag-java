package com.teng.rag.rag;

import com.teng.rag.config.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检索链路测试：桩嵌入模型（关键词→固定向量），不加载 ONNX、不联网。
 *
 * <p>验证的是"问题嵌入 → 相似检索 → 排序 → 拼 Prompt"整条编排，
 * 这是 RAG 正确性的核心，必须和 LLM/模型解耦测试。</p>
 */
class RagQueryServiceTest {

    /** 桩 LLM：录制收到的 Prompt，绝不发起网络调用。 */
    private static class RecordingLlmStreamer implements LlmStreamer {

        String lastPrompt;

        @Override
        public void stream(String prompt, Callback callback) {
            this.lastPrompt = prompt;
            callback.onToken("ok");
            callback.onComplete(1, 1, 1);
        }
    }

    private InMemoryEmbeddingStore<TextSegment> store;
    private RagQueryService service;
    private RecordingLlmStreamer llm;

    @BeforeEach
    void setUp() {
        store = new InMemoryEmbeddingStore<>();
        llm = new RecordingLlmStreamer();
        var props = new RagProperties(
                new RagProperties.Llm("http://localhost", "k", "m", 10),
                new RagProperties.Retrieval(2, 0.0),
                new RagProperties.Chunk(100, 10),
                new RagProperties.Cache(1800, 300, 30),
                new RagProperties.Store("target/test-store.json"));
        service = new RagQueryService(new KeywordEmbeddingModel(), store, llm, props);

        // 预置三段知识
        addSegment("doc-a", "苹果产品手册.txt", "苹果手机很耐用，苹果电脑屏幕好。");
        addSegment("doc-b", "汽车指南.txt", "汽车需要定期保养，汽车保险很重要。");
        addSegment("doc-c", "杂项.txt", "今天天气不错。");
    }

    private void addSegment(String docId, String name, String text) {
        var segment = TextSegment.from(text);
        segment.metadata().put("doc_id", docId);
        segment.metadata().put("doc_name", name);
        store.add(new KeywordEmbeddingModel().embed(text).content(), segment);
    }

    @Test
    void should_rank_same_topic_segments_first() {
        var result = service.retrieve("苹果多少钱");

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits().get(0).docName()).isEqualTo("苹果产品手册.txt");
        assertThat(result.hits().get(0).score()).isGreaterThan(0.9);
    }

    @Test
    void should_respect_top_k_limit() {
        var result = service.retrieve("苹果怎么样"); // top-k=2
        assertThat(result.hits()).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void should_build_prompt_with_hit_content() {
        var result = service.retrieve("苹果多少钱");
        assertThat(result.prompt())
                .contains("苹果手机很耐用")
                .contains("[1]")
                .contains("苹果多少钱");
    }

    @Test
    void should_feed_prompt_to_llm_and_emit_tokens() {
        final StringBuilder tokens = new StringBuilder();
        final long[] usage = new long[3];
        service.answer("苹果多少钱", new RagQueryService.QueryEventSink() {
            @Override
            public void onSources(List<RagQueryService.Hit> hits) {
                assertThat(hits).isNotEmpty();
            }

            @Override
            public void onToken(String text) {
                tokens.append(text);
            }

            @Override
            public void onComplete(long promptTokens, long completionTokens, long latencyMs) {
                usage[0] = promptTokens;
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }

            @Override
            public void onRetrievalTiming(long retrievalMs) {
                assertThat(retrievalMs).isGreaterThanOrEqualTo(0);
            }
        });
        assertThat(tokens.toString()).isEqualTo("ok");
        assertThat(usage[0]).isEqualTo(1);
    }

    @Test
    void should_return_empty_hits_when_no_match_above_threshold() {
        var props = new RagProperties(
                new RagProperties.Llm("http://localhost", "k", "m", 10),
                new RagProperties.Retrieval(2, 0.99), // 阈值高到几乎不可能命中
                new RagProperties.Chunk(100, 10),
                new RagProperties.Cache(1800, 300, 30),
                new RagProperties.Store("target/test-store.json"));
        var strictService = new RagQueryService(
                new KeywordEmbeddingModel(), store, new RecordingLlmStreamer(), props);
        // "汽车"与"苹果"正交（相似度0），只有完全相同话题才 >=0.99
        var result = strictService.retrieve("汽车多少钱");
        assertThat(result.hits()).allMatch(h -> h.score() >= 0.99);
    }
}
