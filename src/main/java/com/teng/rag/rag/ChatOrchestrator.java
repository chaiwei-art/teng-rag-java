package com.teng.rag.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teng.rag.cache.AnswerCache;
import com.teng.rag.cache.AnswerCachePolicy;
import com.teng.rag.store.ChatHistoryEntity;
import com.teng.rag.store.ChatHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 问答编排：缓存 → 检索 → 空命中短路 → 流式生成 → 历史落库。
 *
 * <p>这是"一次提问"的完整决策树（面试画图就画这个）：</p>
 * <pre>
 * 1. 缓存命中？ → 直接回放（sources + 全文一次推），LLM 都不叫，落库 cached=1
 * 2. 检索空命中？ → 短路：不调 LLM（省钱省时），回固定话术 + 负缓存 30s（防穿透）
 * 3. 正常路径 → 流式生成，边收边转发明细，完成后写正缓存（TTL+抖动）并落库
 * </pre>
 *
 * <p>职责拆分：本类只做"决策与编排"，检索是 {@link Retriever}、流式是
 * {@link RagQueryService#streamFromRetrieval}、缓存是 {@link AnswerCache}、
 * 落库是 {@link ChatHistoryStore}——每块可独立替换与单测。</p>
 */
@Service
public class ChatOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatOrchestrator.class);

    static final String NOT_FOUND_MESSAGE =
            "知识库中没有找到相关信息，建议换个问法，或先上传相关文档。";

    private final RagQueryService queryService;
    private final AnswerCache answerCache;
    private final ChatHistoryStore historyStore;
    private final ObjectMapper objectMapper;

    public ChatOrchestrator(RagQueryService queryService,
                            AnswerCache answerCache,
                            ChatHistoryStore historyStore,
                            ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.answerCache = answerCache;
        this.historyStore = historyStore;
        this.objectMapper = objectMapper;
    }

    public void answer(String question, RagQueryService.QueryEventSink sink) {
        long start = System.currentTimeMillis();

        // ---- 1. 缓存 ----
        Optional<AnswerCachePolicy.CachedAnswer> cached = answerCache.get(question);
        if (cached.isPresent()) {
            AnswerCachePolicy.CachedAnswer hit = cached.get();
            sink.onCacheHit(true);
            sink.onSources(hit.sources());
            sink.onToken(hit.answer());
            sink.onRetrievalTiming(0);
            long elapsed = System.currentTimeMillis() - start;
            sink.onComplete(0, 0, elapsed);
            persist(question, hit.answer(), hit.sources(), 0, 0, 0, elapsed, true);
            return;
        }
        sink.onCacheHit(false);

        // ---- 2. 检索 ----
        long retrievalStart = System.currentTimeMillis();
        RagQueryService.Retrieval retrieval = queryService.retrieve(question);
        long retrievalMs = System.currentTimeMillis() - retrievalStart;
        sink.onSources(retrieval.hits());
        sink.onRetrievalTiming(retrievalMs);

        // ---- 2b. 空命中短路：不调 LLM ----
        if (retrieval.hits().isEmpty()) {
            answerCache.putNegative(question, NOT_FOUND_MESSAGE);
            sink.onToken(NOT_FOUND_MESSAGE);
            long elapsed = System.currentTimeMillis() - start;
            sink.onComplete(0, 0, elapsed);
            persist(question, NOT_FOUND_MESSAGE, List.of(), 0, 0, retrievalMs, elapsed, false);
            return;
        }

        // ---- 3. 流式生成（回调线程里完成缓存与落库）----
        StringBuilder answerBuf = new StringBuilder();
        queryService.streamFromRetrieval(retrieval, new RagQueryService.QueryEventSink() {

            @Override
            public void onSources(List<RagQueryService.Hit> hits) {
                // sources 已在编排层发出
            }

            @Override
            public void onToken(String text) {
                answerBuf.append(text);
                sink.onToken(text);
            }

            @Override
            public void onComplete(long promptTokens, long completionTokens, long latencyMs) {
                String answer = answerBuf.toString();
                answerCache.put(question, new AnswerCachePolicy.CachedAnswer(answer, retrieval.hits()));
                persist(question, answer, retrieval.hits(),
                        promptTokens, completionTokens, retrievalMs, latencyMs, false);
                sink.onComplete(promptTokens, completionTokens, latencyMs);
            }

            @Override
            public void onError(Throwable error) {
                // 失败不缓存：下次重新生成（防止把临时故障缓存住）
                sink.onError(error);
            }

            @Override
            public void onRetrievalTiming(long retrievalMsIgnored) {
                // 已发出
            }
        });
    }

    /** 历史落库：失败只告警，绝不影响正在推流的回答。 */
    private void persist(String question, String answer, List<RagQueryService.Hit> sources,
                         long promptTokens, long completionTokens,
                         long retrievalMs, long latencyMs, boolean cached) {
        try {
            ChatHistoryEntity entity = new ChatHistoryEntity();
            entity.setQuestion(question);
            entity.setAnswer(answer);
            entity.setSourcesJson(objectMapper.writeValueAsString(sources));
            entity.setPromptTokens((int) promptTokens);
            entity.setCompletionTokens((int) completionTokens);
            entity.setLatencyMs((int) latencyMs);
            entity.setRetrievalMs((int) retrievalMs);
            entity.setCached(cached);
            historyStore.save(entity);
        } catch (Exception e) {
            log.warn("[history] 落库失败（不影响回答）: {}", e.getMessage());
        }
    }
}
