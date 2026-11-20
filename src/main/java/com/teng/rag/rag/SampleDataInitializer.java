package com.teng.rag.rag;

import com.teng.rag.store.DocMetaEntity;
import com.teng.rag.store.DocumentMetaStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 启动对账 + 种子文档灌入。
 *
 * <p><b>对账</b>（元数据在 MySQL、向量在内存快照，两态可能分叉）：</p>
 * <ul>
 *   <li>向量空但元数据非空 → 快照丢了，向量不可恢复 → 清掉陈旧行（否则"列表有、检索空"）</li>
 *   <li>向量非空但元数据空 → 无主向量（删库/换快照）→ 清掉向量，避免幽灵命中</li>
 * </ul>
 *
 * <p><b>灌种子</b>：两边都空（首次启动）→ 自动摄取 resources/samples 下 3 篇文档，
 * 保证 clone 下来开箱即可演示与跑离线评测。</p>
 *
 * <p>任何失败只告警不阻断启动——演示优先。</p>
 */
@Component
public class SampleDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(SampleDataInitializer.class);

    private final DocumentMetaStore metaStore;
    private final InMemoryEmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore;
    private final DocumentIngestService ingestService;

    public SampleDataInitializer(DocumentMetaStore metaStore,
                                 InMemoryEmbeddingStore<dev.langchain4j.data.segment.TextSegment> embeddingStore,
                                 DocumentIngestService ingestService) {
        this.metaStore = metaStore;
        this.embeddingStore = embeddingStore;
        this.ingestService = ingestService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAndSeed() {
        try {
            boolean vectorsEmpty = embeddingStore.isEmpty();
            int docs = metaStore.count();

            if (vectorsEmpty && docs > 0) {
                log.warn("[init] 向量快照缺失但 doc_meta 有 {} 行，清理不可恢复的陈旧元数据", docs);
                metaStore.clear();
                docs = 0;
            } else if (!vectorsEmpty && docs == 0) {
                log.warn("[init] 存在无主向量（元数据为空），清空向量库");
                embeddingStore.removeAll();
                vectorsEmpty = true;
            }

            if (vectorsEmpty && docs == 0) {
                seedSamples();
            } else {
                log.info("[init] 知识库已有 {} 个文档，跳过种子灌入", docs);
            }
        } catch (Exception e) {
            log.warn("[init] 启动对账/种子灌入失败（服务继续运行）: {}", e.getMessage());
        }
    }

    private void seedSamples() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath:samples/*.md");
        if (resources.length == 0) {
            log.warn("[init] samples/ 下没有种子文档");
            return;
        }
        int seeded = 0;
        for (Resource resource : resources) {
            String name = resource.getFilename();
            if (name == null) {
                continue;
            }
            String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            DocMetaEntity meta = ingestService.ingestText(name, text);
            seeded++;
            log.info("[init] 种子文档已入库: {}（{} 个向量块）", name, meta.getChunkCount());
        }
        log.info("[init] 共灌入 {} 篇种子文档", seeded);
    }
}
