package com.teng.rag.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teng.rag.mapper.ChatHistoryMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** MySQL 实现（MyBatis-Plus）：问答历史落库。 */
@Component
public class MybatisChatHistoryStore implements ChatHistoryStore {

    private final ChatHistoryMapper mapper;

    public MybatisChatHistoryStore(ChatHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(ChatHistoryEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        mapper.insert(entity);
    }

    @Override
    public List<ChatHistoryEntity> findRecent(int limit) {
        return mapper.selectList(new LambdaQueryWrapper<ChatHistoryEntity>()
                .orderByDesc(ChatHistoryEntity::getCreatedAt)
                .orderByDesc(ChatHistoryEntity::getId)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public Optional<ChatHistoryEntity> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    @Override
    public long count() {
        return mapper.selectCount(null);
    }
}
