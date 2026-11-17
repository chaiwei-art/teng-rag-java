package com.teng.rag.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prompt 组装测试：结构完整性 + 空检索兜底 + 来源编号。
 */
class PromptBuilderTest {

    @Test
    void should_include_instruction_context_and_question() {
        String prompt = PromptBuilder.build(
                "退货政策是什么",
                List.of("用户手册.txt\n七天无理由退货", "FAQ.md\n拆封不支持退货"));

        assertThat(prompt).contains("只依据上下文作答");
        assertThat(prompt).contains("[1] 用户手册.txt");
        assertThat(prompt).contains("[2] FAQ.md");
        assertThat(prompt).contains("【用户问题】");
        assertThat(prompt).contains("退货政策是什么");
    }

    @Test
    void should_handle_empty_retrieval() {
        String prompt = PromptBuilder.build("随便问问", List.of());
        assertThat(prompt).contains("本次未检索到相关内容");
        assertThat(prompt).contains("随便问问");
    }

    @Test
    void should_number_contexts_in_retrieval_order() {
        String prompt = PromptBuilder.build("q", List.of("甲", "乙", "丙"));
        int i1 = prompt.indexOf("[1]");
        int i2 = prompt.indexOf("[2]");
        int i3 = prompt.indexOf("[3]");
        assertThat(i1).isLessThan(i2);
        assertThat(i2).isLessThan(i3);
    }
}
