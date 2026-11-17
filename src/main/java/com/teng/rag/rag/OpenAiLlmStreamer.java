package com.teng.rag.rag;

import com.teng.rag.config.RagProperties;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容网关的流式实现（基于 LangChain4j StreamingChatModel）。
 *
 * <p>回调由 SDK 的 HTTP 线程触发，这里只做转发：
 * onPartialResponse → token / onCompleteResponse → 完成统计 / onError → 错误。</p>
 */
@Component
public class OpenAiLlmStreamer implements LlmStreamer {

    private final OpenAiStreamingChatModel chatModel;
    private final RagProperties props;

    public OpenAiLlmStreamer(OpenAiStreamingChatModel chatModel, RagProperties props) {
        this.chatModel = chatModel;
        this.props = props;
    }

    @Override
    public void stream(String prompt, Callback callback) {
        if (!props.llm().hasKey()) {
            callback.onError(new IllegalStateException(
                    "未配置 LLM API Key：请设置环境变量 OPENAI_API_KEY 或 LLM_API_KEY"));
            return;
        }
        long start = System.currentTimeMillis();
        chatModel.chat(prompt, new StreamingChatResponseHandler() {

            @Override
            public void onPartialResponse(String partialResponse) {
                callback.onToken(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                long promptTokens = 0;
                long completionTokens = 0;
                var usage = response.tokenUsage();
                if (usage != null) {
                    promptTokens = usage.inputTokenCount() == null ? 0 : usage.inputTokenCount();
                    completionTokens = usage.outputTokenCount() == null ? 0 : usage.outputTokenCount();
                }
                callback.onComplete(promptTokens, completionTokens, System.currentTimeMillis() - start);
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        });
    }
}
