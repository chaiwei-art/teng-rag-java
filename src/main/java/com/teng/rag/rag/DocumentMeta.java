package com.teng.rag.rag;

import java.time.Instant;
import java.util.List;

/**
 * 已入库文档的元数据。
 *
 * @param id          文档唯一 ID（同时作为向量块的过滤键）
 * @param name        原始文件名
 * @param sizeBytes   文件大小
 * @param chunkCount  切分出的向量块数量
 * @param createdAt   入库时间
 * @param vectorIds   每个向量块在 EmbeddingStore 中的 ID（删除文档时按 ID 批量移除）
 */
public record DocumentMeta(
        String id,
        String name,
        long sizeBytes,
        int chunkCount,
        Instant createdAt,
        List<String> vectorIds
) {
}
