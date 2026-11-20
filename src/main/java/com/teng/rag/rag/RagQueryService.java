package com.teng.rag.rag;

import com.teng.rag.config.RagProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 查询服务（Retrieval 阶段 + 生成阶段的底层能力）。
 *
 * <p>回答问题的完整链路：</p>
 * <ol>
 *   <li>问题 → 嵌入向量（与入库用同一个模型，保证同一语义空间）</li>
 *   <li>向量相似检索 Top-K（带最低分阈值，低于阈值宁可不答）</li>
 *   <li>命中片段拼进 Prompt（带来源编号）</li>
 *   <li>LLM 流式生成</li>
 * </ol>
 *
 * <p>缓存/落库等编排逻辑在 {@link ChatOrchestrator}——本类只保留"检索 + 流式"原子能力，
 * 单层职责清晰，也方便评测（EvalService 只依赖 {@link Retriever}）。</p>
 */
@Service
public class RagQueryService implements Retriever {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final LlmStreamer llmStreamer;
    private final RagProperties props;

    public RagQueryService(EmbeddingModel embeddingModel,
                           EmbeddingStore<TextSegment> embeddingStore,
                           LlmStreamer llmStreamer,
                           RagProperties props) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.llmStreamer = llmStreamer;
        this.props = props;
    }

    /**
     * 检索命中结果（同时携带给前端做"引用来源"展示）。
     */
    public record Hit(String docName, String text, double score) {
    }

    public record Retrieval(List<Hit> hits, String prompt) {
    }

    @Override
    public Retrieval retrieve(String question) {
        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(props.retrieval().topK())
                .minScore(props.retrieval().minScore())
                .build();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(request).matches();

        List<Hit> hits = matches.stream()
                .map(m -> new Hit(
                        m.embedded().metadata().getString("doc_name"),
                        m.embedded().text(),
                        m.score()))
                .toList();

        List<String> contexts = hits.stream().map(h -> h.docName() + "\n" + h.text()).toList();
        return new Retrieval(hits, PromptBuilder.build(question, contexts));
    }

    /**
     * 流式生成：把已检索结果交给 LLM，token 逐个转发给 sink。
     * 不再重复检索/发 sources——编排层（orchestrator）负责事件顺序与缓存。
     */
    public void streamFromRetrieval(Retrieval retrieval, QueryEventSink sink) {
        llmStreamer.stream(retrieval.prompt(), new LlmStreamer.Callback() {
            @Override
            public void onToken(String text) {
                sink.onToken(text);
            }

            @Override
            public void onComplete(long promptTokens, long completionTokens, long latencyMs) {
                sink.onComplete(promptTokens, completionTokens, latencyMs);
            }

            @Override
            public void onError(Throwable error) {
                sink.onError(error);
            }
        });
    }

    /**
     * 一步式问答（检索 + 发 sources + 流式 + 检索计时）。
     * 保留给简单场景与测试；完整链路（缓存/落库）走 ChatOrchestrator。
     */
    public void answer(String question, QueryEventSink sink) {
        long start = System.currentTimeMillis();
        Retrieval retrieval = retrieve(question);
        sink.onSources(retrieval.hits());
        streamFromRetrieval(retrieval, sink);
        sink.onRetrievalTiming(System.currentTimeMillis() - start);
    }

    /** 编排结果回调（Controller 实现为 SSE 推送，测试实现为录制）。 */
    public interface QueryEventSink {
        void onSources(List<Hit> hits);

        void onToken(String text);

        void onComplete(long promptTokens, long completionTokens, long latencyMs);

        void onError(Throwable error);

        void onRetrievalTiming(long retrievalMs);

        /** 是否缓存命中（前端展示 ⚡ 标识）。默认忽略，兼容既有测试桩。 */
        default void onCacheHit(boolean hit) {
        }
    }
}
