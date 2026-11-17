package com.teng.rag.rag;

import com.teng.rag.config.RagProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文档切分器（Chunking）。
 *
 * <p>用 LangChain4j 的递归切分器：按段落 → 句子 → 字符逐级下钻，
 * 控制单段最大长度与相邻段重叠（overlap 用来缓解"切断上下文"问题）。</p>
 *
 * <p>为什么需要切分（面试高频）：</p>
 * <ul>
 *   <li>整篇文档塞不进 Prompt，且越长越慢越贵</li>
 *   <li>一整篇压成一个向量，检索区分度差（不同主题互相稀释）</li>
 *   <li>小段落 → 每段向量聚焦单一主题 → 相似度检索更准</li>
 * </ul>
 */
@Component
public class TextChunker {

    private final int maxSegmentSize;
    private final int maxOverlapSize;

    public TextChunker(RagProperties props) {
        this.maxSegmentSize = props.chunk().maxSegmentSize();
        this.maxOverlapSize = props.chunk().maxOverlapSize();
    }

    /**
     * 把单个文档切分为带 metadata 的 TextSegment 列表。
     * 每段会携带 doc_id / doc_name，供检索结果回溯来源（引用溯源的基础）。
     */
    public List<TextSegment> split(Document document, String docId) {
        var splitter = DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize);
        List<TextSegment> segments = splitter.splitAll(List.of(document));
        for (TextSegment segment : segments) {
            segment.metadata().put("doc_id", docId);
            segment.metadata().put("doc_name", document.metadata().getString("file_name"));
        }
        return segments;
    }
}
