-- ============================================================
-- teng_rag 库表结构（spring.sql.init 每次启动幂等执行）
-- ============================================================

-- 文档元数据（向量本身在内存 EmbeddingStore，这里存业务信息与向量块 ID）
CREATE TABLE IF NOT EXISTS doc_meta (
    id           VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '文档ID',
    name         VARCHAR(255) NOT NULL COMMENT '原始文件名',
    size_bytes   BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小',
    chunk_count  INT          NOT NULL DEFAULT 0 COMMENT '切分出的向量块数',
    vector_ids   TEXT         NOT NULL COMMENT '向量块ID列表，逗号分隔（级联删除用）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库文档元数据';

-- 对话历史（每次问答落库，可回放/审计/统计）
CREATE TABLE IF NOT EXISTS chat_history (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    question          TEXT         NOT NULL COMMENT '用户问题',
    answer            MEDIUMTEXT  NOT NULL COMMENT '模型完整回答',
    sources_json      TEXT         NULL COMMENT '引用来源快照(JSON)',
    prompt_tokens     INT          NOT NULL DEFAULT 0,
    completion_tokens INT          NOT NULL DEFAULT 0,
    latency_ms        INT          NOT NULL DEFAULT 0 COMMENT '端到端耗时',
    retrieval_ms      INT          NOT NULL DEFAULT 0 COMMENT '检索耗时',
    cached            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否缓存命中',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '问答历史';
