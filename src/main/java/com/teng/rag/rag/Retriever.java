package com.teng.rag.rag;

/**
 * 检索能力抽象：问题 → 命中片段。
 *
 * <p>抽接口的用途——离线评测（EvalService）只依赖"检索"这一件事，
 * 单测可以注入桩检索器精确断言命中率，不必搭真实向量库。</p>
 */
public interface Retriever {

    RagQueryService.Retrieval retrieve(String question);
}
