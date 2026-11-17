package com.teng.rag.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 元数据注册表测试（将来换成 MySQL 实现时，这组测试保持不变——接口契约的意义）。
 */
class DocumentRegistryTest {

    @Test
    void should_save_find_and_remove() {
        DocumentRegistry registry = new DocumentRegistry();
        registry.save(DocumentRegistry.create("a1", "手册.txt", 1024, 5, List.of("v1", "v2")));

        assertThat(registry.count()).isEqualTo(1);
        assertThat(registry.findById("a1")).isPresent();
        assertThat(registry.remove("a1")).isPresent();
        assertThat(registry.count()).isZero();
        assertThat(registry.findById("a1")).isEmpty();
    }

    @Test
    void should_list_newest_first() {
        DocumentRegistry registry = new DocumentRegistry();
        registry.save(DocumentRegistry.create("old", "旧文档", 1, 1, List.of()));
        try {
            Thread.sleep(5);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        registry.save(DocumentRegistry.create("new", "新文档", 1, 1, List.of()));

        assertThat(registry.findAll())
                .extracting(DocumentMeta::id)
                .containsExactly("new", "old");
    }

    @Test
    void should_return_zero_when_removing_unknown() {
        assertThat(new DocumentRegistry().remove("nope")).isEmpty();
    }
}
