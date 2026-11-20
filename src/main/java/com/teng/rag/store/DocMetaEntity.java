package com.teng.rag.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档元数据实体 → 表 doc_meta。
 *
 * <p>向量本体在内存 EmbeddingStore，这里只存业务信息 + 向量块 ID 列表
 * （逗号分隔，删除文档时按 ID 级联清理向量）。</p>
 */
@Data
@TableName("doc_meta")
public class DocMetaEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;

    private Long sizeBytes;

    private Integer chunkCount;

    /** 向量块 ID，逗号分隔 */
    private String vectorIds;

    private LocalDateTime createdAt;
}
