package com.teng.rag.rag;

import java.util.List;

/**
 * LLM 流式输出抽象。
 *
 * <p>为什么抽接口：RAG 编排逻辑（检索→拼 Prompt→生成）不该和具体模型 SDK 绑死。
 * 单测时注入桩实现录制调用；换模型供应商只换实现类。</p>
 */
public interface LlmStreamer {

    /**
     * 流式生成回答。
     *
     * @param prompt   完整 Prompt
     * @param callback 流式回调（token / 完成 / 错误）
     */
    void stream(String prompt, Callback callback);

    interface Callback {
        /** 收到一段增量文本 */
        void onToken(String text);

        /**
         * 生成完成。
         *
         * @param promptTokens     输入 token 数
         * @param completionTokens 输出 token 数
         * @param latencyMs        端到端耗时
         */
        void onComplete(long promptTokens, long completionTokens, long latencyMs);

        /** 生成失败 */
        void onError(Throwable error);
    }
}
