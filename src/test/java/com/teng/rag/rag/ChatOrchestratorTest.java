package com.teng.rag.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teng.rag.cache.AnswerCachePolicy;
import com.teng.rag.cache.InMemoryAnswerCache;
import com.teng.rag.config.RagProperties;
import com.teng.rag.store.InMemoryChatHistoryStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 编排决策树测试：缓存命中跳过 LLM / 空命中短路 / 未命中落库 / 失败不缓存。
 *
 * <p>桩：关键词嵌入（排序可断言）+ 假 LLM（统计调用次数，绝不联网）。
 * 整套测试毫秒级、零外部依赖——编排逻辑是面试追问最深的地方，必须测透。</p>
 */
class ChatOrchestratorTest {

    /** 假 LLM：统计调用次数，可切换失败模式。 */
    private static class FakeLlm implements LlmStreamer {
        int calls;
        boolean fail;

        @Override
        public void stream(String prompt, Callback callback) {
            calls++;
            if (fail) {
                callback.onError(new RuntimeException("LLM 挂了"));
                return;
            }
            callback.onToken("答案[1]");
            callback.onComplete(10, 5, 7);
        }
    }

    /** 录制 sink：捕获编排层发出的全部事件。 */
    private static class RecordingSink implements RagQueryService.QueryEventSink {
        final StringBuilder tokens = new StringBuilder();
        List<RagQueryService.Hit> sources = List.of();
        Boolean cacheHit;
        long promptTokens = -1;
        boolean completed;
        Throwable error;
        long retrievalMs = -1;

        @Override
        public void onSources(List<RagQueryService.Hit> hits) {
            this.sources = hits;
        }

        @Override
        public void onToken(String text) {
            tokens.append(text);
        }

        @Override
        public void onComplete(long promptTokens, long completionTokens, long latencyMs) {
            this.promptTokens = promptTokens;
            this.completed = true;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onRetrievalTiming(long retrievalMs) {
            this.retrievalMs = retrievalMs;
        }

        @Override
        public void onCacheHit(boolean hit) {
            this.cacheHit = hit;
        }
    }

    private FakeLlm llm;
    private InMemoryAnswerCache cache;
    private InMemoryChatHistoryStore history;
    private ChatOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        llm = new FakeLlm();
        var props = new RagProperties(
                new RagProperties.Llm("http://localhost", "k", "m", 10),
                new RagProperties.Retrieval(2, 0.6), // relevance=(1+cos)/2：正交=0.5 被过滤，同话题=1.0 命中
                new RagProperties.Chunk(100, 10),
                new RagProperties.Cache(1800, 300, 30),
                new RagProperties.Store("target/test-store.json"));

        cache = new InMemoryAnswerCache(new AnswerCachePolicy(props.cache()));
        history = new InMemoryChatHistoryStore();

        var store = new InMemoryEmbeddingStore<TextSegment>();
        var segment = TextSegment.from("苹果手机很耐用，苹果电脑屏幕好。");
        segment.metadata().put("doc_id", "doc-a");
        segment.metadata().put("doc_name", "苹果产品手册.txt");
        store.add(new KeywordEmbeddingModel().embed(segment.text()).content(), segment);

        var queryService = new RagQueryService(new KeywordEmbeddingModel(), store, llm, props);
        orchestrator = new ChatOrchestrator(queryService, cache, history, new ObjectMapper());
    }

    @Test
    void should_stream_and_persist_on_cache_miss() {
        RecordingSink sink = new RecordingSink();
        orchestrator.answer("苹果价格呢", sink);

        assertThat(sink.cacheHit).isFalse();
        assertThat(llm.calls).isEqualTo(1);
        assertThat(sink.tokens.toString()).isEqualTo("答案[1]");
        assertThat(sink.promptTokens).isEqualTo(10);
        assertThat(sink.completed).isTrue();
        assertThat(sink.sources).isNotEmpty();
        assertThat(sink.retrievalMs).isGreaterThanOrEqualTo(0);

        var rows = history.findRecent(10);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getQuestion()).isEqualTo("苹果价格呢");
        assertThat(rows.get(0).getAnswer()).isEqualTo("答案[1]");
        assertThat(rows.get(0).getCached()).isFalse();
        assertThat(rows.get(0).getSourcesJson()).contains("苹果产品手册.txt");
    }

    @Test
    void should_hit_cache_and_skip_llm_on_second_call() {
        orchestrator.answer("苹果价格呢", new RecordingSink());
        assertThat(llm.calls).isEqualTo(1);

        RecordingSink second = new RecordingSink();
        orchestrator.answer("苹果价格呢", second);

        assertThat(second.cacheHit).isTrue();
        assertThat(llm.calls).isEqualTo(1);          // LLM 没有被再次调用
        assertThat(second.tokens.toString()).isEqualTo("答案[1]"); // 全文回放
        assertThat(second.promptTokens).isZero();    // 缓存命中不产生 token 消耗
        assertThat(second.sources).isNotEmpty();     // 引用来源随缓存回放
        assertThat(second.completed).isTrue();

        var rows = history.findRecent(10);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getCached()).isTrue(); // 最新一条标记为缓存命中
    }

    @Test
    void should_short_circuit_when_retrieval_is_empty() {
        RecordingSink sink = new RecordingSink();
        orchestrator.answer("香蕉口味如何", sink); // 与库内内容正交 → 0 命中

        assertThat(llm.calls).isZero();                                  // 没检索到就不调 LLM
        assertThat(sink.tokens.toString()).isEqualTo(ChatOrchestrator.NOT_FOUND_MESSAGE);
        assertThat(sink.cacheHit).isFalse();
        assertThat(sink.completed).isTrue();
        assertThat(history.findRecent(10).get(0).getAnswer())
                .isEqualTo(ChatOrchestrator.NOT_FOUND_MESSAGE);

        // 负缓存生效：第二次直接命中，仍然不调 LLM
        RecordingSink second = new RecordingSink();
        orchestrator.answer("香蕉口味如何", second);
        assertThat(llm.calls).isZero();
        assertThat(second.cacheHit).isTrue();
        assertThat(second.tokens.toString()).isEqualTo(ChatOrchestrator.NOT_FOUND_MESSAGE);
    }

    @Test
    void should_not_cache_when_llm_fails() {
        llm.fail = true;
        RecordingSink failed = new RecordingSink();
        orchestrator.answer("苹果价格呢", failed);

        assertThat(failed.error).isNotNull();
        assertThat(failed.completed).isFalse();
        assertThat(history.findRecent(10)).isEmpty(); // 失败不落库

        llm.fail = false;
        RecordingSink retry = new RecordingSink();
        orchestrator.answer("苹果价格呢", retry);

        assertThat(retry.cacheHit).isFalse();   // 失败没被缓存住 → 下次重新生成
        assertThat(llm.calls).isEqualTo(2);
        assertThat(retry.completed).isTrue();
    }
}
