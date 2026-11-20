package com.teng.rag.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teng.rag.config.RagProperties;
import com.teng.rag.rag.RagQueryService;
import com.teng.rag.rag.Retriever;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线评测测试：验证数据集完整性 + 报告计算正确性。
 * 用桩检索器（恒命中/恒不命中）断言指标，不依赖真实向量库。
 */
class EvalServiceTest {

    private static RagProperties props() {
        return new RagProperties(
                new RagProperties.Llm("http://localhost", "k", "m", 10),
                new RagProperties.Retrieval(4, 0.35),
                new RagProperties.Chunk(100, 10),
                new RagProperties.Cache(1800, 300, 30),
                new RagProperties.Store("target/test-store.json"));
    }

    private static List<EvalService.EvalCase> loadDataset() throws Exception {
        try (InputStream in = new ClassPathResource("eval/dataset.json").getInputStream()) {
            return new ObjectMapper().readValue(in,
                    new com.fasterxml.jackson.core.type.TypeReference<List<EvalService.EvalCase>>() {
                    });
        }
    }

    @Test
    void dataset_should_have_20_cases_with_docs_from_samples() throws Exception {
        List<EvalService.EvalCase> dataset = loadDataset();
        assertThat(dataset).hasSize(20);
        assertThat(dataset).allSatisfy(c -> {
            assertThat(c.id()).isNotBlank();
            assertThat(c.question()).isNotBlank();
            assertThat(c.expectDoc()).endsWith(".md");
        });
        // 三篇种子文档都要有覆盖
        assertThat(dataset).extracting(EvalService.EvalCase::expectDoc).containsOnly(
                "退货政策.md", "会员制度.md", "发货与物流.md");
    }

    @Test
    void should_report_full_recall_when_retriever_hits_every_case() throws Exception {
        Map<String, String> questionToDoc = new HashMap<>();
        for (EvalService.EvalCase c : loadDataset()) {
            questionToDoc.put(c.question(), c.expectDoc());
        }
        // 桩检索器：永远返回期望文档（模拟"检索完美"）
        Retriever perfect = q -> new RagQueryService.Retrieval(
                List.of(new RagQueryService.Hit(questionToDoc.get(q), "内容", 0.9)), "prompt");

        EvalService.EvalReport report = new EvalService(perfect, new ObjectMapper(), props()).run();

        assertThat(report.total()).isEqualTo(20);
        assertThat(report.hitAtK()).isEqualTo(20);
        assertThat(report.hitRate()).isEqualTo(1.0);
        assertThat(report.avgHitScore()).isCloseTo(0.9, org.assertj.core.data.Offset.offset(0.001));
        assertThat(report.topK()).isEqualTo(4);
        assertThat(report.rows()).hasSize(20);
        assertThat(report.rows()).allMatch(EvalService.EvalRow::hit);
    }

    @Test
    void should_report_zero_recall_when_nothing_retrieved() {
        Retriever nothing = q -> new RagQueryService.Retrieval(List.of(), "prompt");

        EvalService.EvalReport report = new EvalService(nothing, new ObjectMapper(), props()).run();

        assertThat(report.total()).isEqualTo(20);
        assertThat(report.hitAtK()).isZero();
        assertThat(report.hitRate()).isZero();
        assertThat(report.avgHitScore()).isZero();
        assertThat(report.rows()).allMatch(r -> !r.hit());
        assertThat(report.rows()).allMatch(r -> r.topDoc() == null);
    }
}
