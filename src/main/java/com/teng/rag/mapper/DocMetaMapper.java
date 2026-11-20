package com.teng.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teng.rag.store.DocMetaEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * doc_meta 表 Mapper：MyBatis-Plus BaseMapper 直接提供单表 CRUD，
 * 无 XML（单表操作没必要写 XML，复杂 SQL 再补）。
 */
@Mapper
public interface DocMetaMapper extends BaseMapper<DocMetaEntity> {
}
