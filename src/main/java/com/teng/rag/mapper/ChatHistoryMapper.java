package com.teng.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teng.rag.store.ChatHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * chat_history 表 Mapper。
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistoryEntity> {
}
