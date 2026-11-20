package com.teng.rag.store;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 元数据存储契约测试（内存实现）。
 * MyBatis 实现遵循同一接口契约——换存储时这组测试保持不变，这就是接口的意义。
 */
class InMemoryDocumentMetaStoreTest {

    private static DocMetaEntity meta(String id, String name, LocalDateTime createdAt) {
        DocMetaEntity entity = new DocMetaEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setSizeBytes(1024L);
        entity.setChunkCount(5);
        entity.setVectorIds("v1,v2");
        entity.setCreatedAt(createdAt);
        return entity;
    }

    @Test
    void should_save_find_and_remove() {
        DocumentMetaStore store = new InMemoryDocumentMetaStore();
        store.save(meta("a1", "手册.txt", LocalDateTime.now()));

        assertThat(store.count()).isEqualTo(1);
        assertThat(store.findById("a1")).isPresent();
        assertThat(store.remove("a1")).isPresent();
        assertThat(store.count()).isZero();
        assertThat(store.findById("a1")).isEmpty();
    }

    @Test
    void should_list_newest_first() {
        DocumentMetaStore store = new InMemoryDocumentMetaStore();
        LocalDateTime now = LocalDateTime.now();
        store.save(meta("old", "旧文档", now.minusMinutes(5)));
        store.save(meta("new", "新文档", now));

        assertThat(store.findAll())
                .extracting(DocMetaEntity::getId)
                .containsExactly("new", "old");
    }

    @Test
    void should_return_empty_when_removing_unknown() {
        assertThat(new InMemoryDocumentMetaStore().remove("nope")).isEmpty();
    }

    @Test
    void should_clear_all() {
        DocumentMetaStore store = new InMemoryDocumentMetaStore();
        store.save(meta("a", "a.txt", LocalDateTime.now()));
        store.save(meta("b", "b.txt", LocalDateTime.now()));
        store.clear();
        assertThat(store.count()).isZero();
        assertThat(store.findAll()).isEmpty();
    }

    @Test
    void should_round_trip_vector_ids_as_string() {
        DocumentMetaStore store = new InMemoryDocumentMetaStore();
        store.save(meta("a1", "手册.txt", LocalDateTime.now()));
        DocMetaEntity loaded = store.findById("a1").orElseThrow();
        assertThat(List.of(loaded.getVectorIds().split(","))).containsExactly("v1", "v2");
    }
}
