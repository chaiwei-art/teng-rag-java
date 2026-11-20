package com.teng.rag.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

/**
 * 共享测试桩嵌入：含"苹果"的文本 → 向量A，含"汽车"的 → 向量B，其他 → 向量C。
 * 同向量余弦相似度=1，正交=0 → 检索排序可精确断言。
 * 不加载 ONNX、不联网，供检索/编排/评测各测试复用。
 */
class KeywordEmbeddingModel implements EmbeddingModel {

    private static final Map<String, float[]> VECTORS = Map.of(
            "apple", new float[]{1f, 0f, 0f},
            "car", new float[]{0f, 1f, 0f},
            "other", new float[]{0f, 0f, 1f}
    );

    private Embedding vectorFor(String text) {
        if (text.contains("苹果")) return new Embedding(VECTORS.get("apple"));
        if (text.contains("汽车")) return new Embedding(VECTORS.get("car"));
        return new Embedding(VECTORS.get("other"));
    }

    @Override
    public dev.langchain4j.model.output.Response<Embedding> embed(String text) {
        return dev.langchain4j.model.output.Response.from(vectorFor(text));
    }

    @Override
    public dev.langchain4j.model.output.Response<List<Embedding>> embedAll(
            List<TextSegment> segments) {
        List<Embedding> embeddings = segments.stream().map(s -> vectorFor(s.text())).toList();
        return dev.langchain4j.model.output.Response.from(embeddings);
    }
}
