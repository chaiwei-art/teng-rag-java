package com.teng.rag.store;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答历史实体 → 表 chat_history。
 * 每次回答落库：可回放、可审计、可做使用量统计。
 */
@Data
@TableName("chat_history")
public class ChatHistoryEntity {

    /** 雪花 ID，自增交给数据库更直观，这里用数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String question;

    private String answer;

    /** 引用来源快照 JSON */
    private String sourcesJson;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer latencyMs;

    private Integer retrievalMs;

    private Boolean cached;

    private LocalDateTime createdAt;
}
