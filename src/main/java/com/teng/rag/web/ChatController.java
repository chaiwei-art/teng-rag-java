package com.teng.rag.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teng.rag.rag.ChatOrchestrator;
import com.teng.rag.rag.RagQueryService;
import com.teng.rag.store.ChatHistoryEntity;
import com.teng.rag.store.ChatHistoryStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 问答接口：
 * POST /api/chat      → SSE 流（编排：缓存/检索/生成/落库）
 * GET  /api/history   → 最近问答历史（MySQL 持久化）
 *
 * <p>SSE 事件协议（与项目内前端约定）：</p>
 * <pre>
 * meta    {model}                          连接建立
 * cache   {hit}                           是否缓存命中（⚡ 标识）
 * sources {items:[{docName,text,score}]}  引用来源（检索先于生成）
 * token   {text}                          增量文本（缓存命中时一次性推全文）
 * retrieval {latencyMs}                   检索耗时
 * usage   {promptTokens, completionTokens, latencyMs}
 * done    {}
 * error   {message}
 * </pre>
 *
 * <p>线程模型：LLM 回调发生在 SDK 的 HTTP 线程，SseEmitter 的 send 是线程安全的；
 * 检索（同步、毫秒级）放在受控线程池里做，避免长时间占用 Tomcat 请求线程。</p>
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatOrchestrator orchestrator;
    private final ChatHistoryStore historyStore;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "rag-query");
        t.setDaemon(true);
        return t;
    });

    public ChatController(ChatOrchestrator orchestrator, ChatHistoryStore historyStore, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.historyStore = historyStore;
        this.objectMapper = objectMapper;
    }

    public record ChatRequest(String question) {
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        String question = request.question() == null ? "" : request.question().trim();
        SseEmitter emitter = new SseEmitter(300_000L);

        if (question.isEmpty()) {
            emitError(emitter, "问题不能为空");
            emitter.complete();
            return emitter;
        }

        executor.submit(() -> {
            try {
                orchestrator.answer(question, new SseQuerySink(emitter));
            } catch (Exception e) {
                emitError(emitter, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                try {
                    emitter.complete();
                } catch (Exception ignore) {
                    // 客户端可能已断开
                }
            }
        });
        return emitter;
    }

    /** 最近问答历史（倒序）。 */
    @GetMapping("/history")
    public List<ChatHistoryEntity> history(@RequestParam(defaultValue = "50") int limit) {
        return historyStore.findRecent(Math.min(Math.max(limit, 1), 200));
    }

    /** 把编排事件翻译成 SSE 帧。 */
    private class SseQuerySink implements RagQueryService.QueryEventSink {

        private final SseEmitter emitter;

        SseQuerySink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onCacheHit(boolean hit) {
            send(emitter, "cache", Map.of("hit", hit));
        }

        @Override
        public void onSources(List<RagQueryService.Hit> hits) {
            send(emitter, "sources", Map.of("items", hits.stream()
                    .map(h -> Map.of("docName", h.docName(), "text", h.text(), "score", h.score()))
                    .toList()));
        }

        @Override
        public void onToken(String text) {
            send(emitter, "token", Map.of("text", text));
        }

        @Override
        public void onComplete(long promptTokens, long completionTokens, long latencyMs) {
            send(emitter, "usage", Map.of(
                    "promptTokens", promptTokens,
                    "completionTokens", completionTokens,
                    "latencyMs", latencyMs));
            send(emitter, "done", Map.of());
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 已断开
            }
        }

        @Override
        public void onError(Throwable error) {
            emitError(emitter, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            try {
                emitter.complete();
            } catch (Exception ignore) {
                // 已断开
            }
        }

        @Override
        public void onRetrievalTiming(long retrievalMs) {
            send(emitter, "retrieval", Map.of("latencyMs", retrievalMs));
        }
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(event).data(json));
        } catch (IOException | IllegalStateException ignore) {
            // 客户端断开等，忽略
        }
    }

    private void emitError(SseEmitter emitter, String message) {
        send(emitter, "error", Map.of("message", message == null ? "未知错误" : message));
    }
}
