package com.teng.rag.store;

import com.teng.rag.mapper.DocMetaMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 实现（MyBatis-Plus）：生产默认。
 * 单表 CRUD 全部走 BaseMapper，复杂查询再写 XML/注解 SQL。
 */
@Component
public class MybatisDocumentMetaStore implements DocumentMetaStore {

    private final DocMetaMapper mapper;

    public MybatisDocumentMetaStore(DocMetaMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(DocMetaEntity meta) {
        if (meta.getCreatedAt() == null) {
            meta.setCreatedAt(LocalDateTime.now());
        }
        mapper.insert(meta);
    }

    @Override
    public Optional<DocMetaEntity> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public List<DocMetaEntity> findAll() {
        return mapper.selectList(null).stream()
                .sorted(Comparator.comparing(DocMetaEntity::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public Optional<DocMetaEntity> remove(String id) {
        DocMetaEntity existing = mapper.selectById(id);
        if (existing == null) {
            return Optional.empty();
        }
        mapper.deleteById(id);
        return Optional.of(existing);
    }

    @Override
    public int count() {
        return mapper.selectCount(null).intValue();
    }

    @Override
    public void clear() {
        mapper.delete(null);
    }
}
