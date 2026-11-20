package com.teng.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teng.rag.config.RagProperties;
import com.teng.rag.rag.Retriever;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * 离线检索评测：跑 eval/dataset.json 标注集，统计召回指标。
 *
 * <p><b>为什么是"离线"评测（面试可讲）</b>：只评估检索层（Retriever），
 * 不调 LLM——一次运行零 token 成本、毫秒级、结果可复现。
 * RAG 的质量瓶颈多数在检索：检索不到的东西，再强的模型也编不出来。
 * 先把召回率修上去，再谈 Prompt 优化。</p>
 *
 * <p>指标：hit@1 / hit@K（期望文档是否进 Top-K，K=retrieval.topK）、
 * 命中样本的平均相似度、平均检索耗时。</p>
 */
@Service
public class EvalService {

    private final Retriever retriever;
    private final ObjectMapper objectMapper;
    private final int topK;

    public EvalService(Retriever retriever, ObjectMapper objectMapper, RagProperties props) {
        this.retriever = retriever;
        this.objectMapper = objectMapper;
        this.topK = props.retrieval().topK();
    }

    /** 标注样本：问题 + 期望命中的文档名 */
    public record EvalCase(String id, String question, String expectDoc) {
    }

    /** 单条结果 */
    public record EvalRow(String id, String question, String expectDoc, boolean hit,
                          String topDoc, double topScore, long latencyMs) {
    }

    /** 汇总报告 */
    public record EvalReport(int total, int hitAtK, double hitRate,
                             double avgHitScore, long avgLatencyMs,
                             int topK, List<EvalRow> rows) {
    }

    /** 执行评测（同步；数据集 20 条，本地嵌入毫秒级/条）。 */
    public EvalReport run() {
        List<EvalCase> dataset = loadDataset();
        if (dataset.isEmpty()) {
            return new EvalReport(0, 0, 0, 0, 0, topK, List.of());
        }

        List<EvalRow> rows = new java.util.ArrayList<>(dataset.size());
        int hits = 0;
        double hitScoreSum = 0;
        long latencySum = 0;

        for (EvalCase evalCase : dataset) {
            long start = System.currentTimeMillis();
            var retrieval = retriever.retrieve(evalCase.question());
            long latency = System.currentTimeMillis() - start;
            latencySum += latency;

            var top = retrieval.hits().isEmpty() ? null : retrieval.hits().get(0);
            boolean hit = retrieval.hits().stream()
                    .anyMatch(h -> evalCase.expectDoc().equals(h.docName()));

            if (hit) {
                hits++;
                // 命中样本里"排第一的那个（可能是期望文档）"的分数
                double score = retrieval.hits().stream()
                        .filter(h -> evalCase.expectDoc().equals(h.docName()))
                        .mapToDouble(h -> h.score())
                        .max()
                        .orElse(0);
                hitScoreSum += score;
            }

            rows.add(new EvalRow(
                    evalCase.id(),
                    evalCase.question(),
                    evalCase.expectDoc(),
                    hit,
                    top == null ? null : top.docName(),
                    top == null ? 0 : top.score(),
                    latency));
        }

        return new EvalReport(
                dataset.size(),
                hits,
                Math.round(hits * 10000.0 / dataset.size()) / 10000.0,
                hits == 0 ? 0 : Math.round(hitScoreSum * 10000.0 / hits) / 10000.0,
                latencySum / dataset.size(),
                topK,
                rows);
    }

    private List<EvalCase> loadDataset() {
        try (InputStream in = new ClassPathResource("eval/dataset.json").getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("评测数据集加载失败: " + e.getMessage(), e);
        }
    }
}
