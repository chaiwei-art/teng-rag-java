package com.teng.rag.rag;

import com.teng.rag.config.RagProperties;
import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 切分器测试：粒度控制 + 来源元数据（引用溯源的根基）。
 */
class TextChunkerTest {

    private TextChunker chunker() {
        var props = new com.teng.rag.config.RagProperties(
                new com.teng.rag.config.RagProperties.Llm("http://localhost", "k", "m", 10),
                new com.teng.rag.config.RagProperties.Retrieval(4, 0.35),
                new com.teng.rag.config.RagProperties.Chunk(100, 20),
                new com.teng.rag.config.RagProperties.Cache(1800, 300, 30),
                new com.teng.rag.config.RagProperties.Store("target/test-store.json"));
        return new TextChunker(props);
    }

    private static Document doc(String text) {
        var d = Document.from(text);
        d.metadata().put("file_name", "测试文档.txt");
        return d;
    }

    @Test
    void should_split_long_text_into_multiple_segments() {
        String text = "第一段内容。".repeat(60); // ~390 字
        List<dev.langchain4j.data.segment.TextSegment> segments = chunker().split(doc(text), "doc1");
        assertThat(segments).hasSizeGreaterThan(1);
    }

    @Test
    void should_limit_segment_size() {
        String text = "这是一个很长的段落。".repeat(80); // ~400 字
        List<dev.langchain4j.data.segment.TextSegment> segments = chunker().split(doc(text), "doc1");
        for (var seg : segments) {
            // 递归切分保证单段不超过上限（按字符计）
            assertThat(seg.text().length()).isLessThanOrEqualTo(100);
        }
    }

    @Test
    void should_stamp_source_metadata_for_citation() {
        List<dev.langchain4j.data.segment.TextSegment> segments =
                chunker().split(doc("任意内容"), "abc123");
        assertThat(segments).isNotEmpty();
        for (var seg : segments) {
            assertThat(seg.metadata().getString("doc_id")).isEqualTo("abc123");
            assertThat(seg.metadata().getString("doc_name")).isEqualTo("测试文档.txt");
        }
    }

    @Test
    void should_keep_overlap_between_segments() {
        // 100 字上限 + 20 字重叠 → 相邻段应有公共内容
        String text = "一二三四五六七八九十".repeat(30); // 300 字
        List<dev.langchain4j.data.segment.TextSegment> segments = chunker().split(doc(text), "d");
        assertThat(segments.size()).isGreaterThan(1);
        String first = segments.get(0).text();
        String second = segments.get(1).text();
        boolean overlapped = false;
        for (int i = 0; i + 5 <= first.length(); i++) {
            if (second.contains(first.substring(i, i + 5))) {
                overlapped = true;
                break;
            }
        }
        assertThat(overlapped).isTrue();
    }
}
