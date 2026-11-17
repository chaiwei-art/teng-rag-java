package com.teng.rag.rag;

import java.util.List;

/**
 * Prompt 组装（纯函数，便于单测）。
 *
 * <p>结构：系统指令 + 检索到的上下文（带来源标注）+ 用户问题。</p>
 * <p>关键设计：每段上下文都带 <b>[来源: 文件名 #段号]</b>，
 * LLM 回答时被要求引用来源编号——这就是"答案溯源"的提示词侧实现。</p>
 */
public final class PromptBuilder {

    public static final String SYSTEM_INSTRUCTION = """
            你是企业知识库问答助手。请严格根据下方【知识库上下文】回答用户问题：
            1. 只依据上下文作答，不得编造上下文中不存在的信息；
            2. 引用上下文时标注来源编号，如 [1]、[2]；
            3. 若上下文不足以回答，明确说"知识库中没有找到相关信息"，并建议用户换个问法；
            4. 使用与用户提问相同的语言回答（中文问就中文答）。
            """;

    private PromptBuilder() {
    }

    /**
     * @param question 用户问题
     * @param contexts 检索命中的上下文（顺序 = 相似度降序）
     * @return 完整 Prompt
     */
    public static String build(String question, List<String> contexts) {
        StringBuilder sb = new StringBuilder();
        sb.append(SYSTEM_INSTRUCTION).append("\n\n【知识库上下文】\n");
        if (contexts.isEmpty()) {
            sb.append("（本次未检索到相关内容）\n");
        } else {
            for (int i = 0; i < contexts.size(); i++) {
                sb.append("[").append(i + 1).append("] ").append(contexts.get(i)).append("\n---\n");
            }
        }
        sb.append("\n【用户问题】\n").append(question);
        return sb.toString();
    }
}
