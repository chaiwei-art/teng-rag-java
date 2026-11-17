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
 * RAG 查询编排（Retrieval 阶段 + 生成阶段）。
 *
 * <p>回答问题的完整链路：</p>
 * <ol>
 *   <li>问题 → 嵌入向量（与入库用同一个模型，保证同一语义空间）</li>
 *   <li>向量相似检索 Top-K（带最低分阈值，低于阈值宁可不答）</li>
 *   <li>命中片段拼进 Prompt（带来源编号）</li>
 *   <li>LLM 流式生成，先推 sources 事件再推 token 流</li>
 * </ol>
 */
@Service
public class RagQueryService {

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

    /** 纯检索：问题 → 命中片段。单独拆出来便于测试。 */
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
     * 流式问答：先推 sources（引用来源），再推 token 流，最后推 usage。
     */
    public void answer(String question, QueryEventSink sink) {
        long start = System.currentTimeMillis();
        Retrieval retrieval = retrieve(question);
        sink.onSources(retrieval.hits());

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
        // 注：latency 由 LlmStreamer 内部计时上报，这里 start 仅作检索耗时参考
        long retrievalMs = System.currentTimeMillis() - start;
        sink.onRetrievalTiming(retrievalMs);
    }

    /** 编排结果回调（Controller 实现为 SSE 推送，测试实现为录制）。 */
    public interface QueryEventSink {
        void onSources(List<Hit> hits);

        void onToken(String text);

        void onComplete(long promptTokens, long completionTokens, long latencyMs);

        void onError(Throwable error);

        void onRetrievalTiming(long retrievalMs);
    }
}
