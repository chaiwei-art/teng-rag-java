package com.teng.rag.web;

import com.teng.rag.rag.DocumentIngestService;
import com.teng.rag.store.DocMetaEntity;
import com.teng.rag.store.DocumentMetaStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文档管理接口：
 * POST   /api/documents        上传文档（txt/md/pdf）
 * GET    /api/documents        已入库文档列表（MySQL 持久化）
 * DELETE /api/documents/{id}   删除文档（向量块 + 元数据 + 缓存一并清理）
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestService ingestService;
    private final DocumentMetaStore metaStore;

    public DocumentController(DocumentIngestService ingestService, DocumentMetaStore metaStore) {
        this.ingestService = ingestService;
        this.metaStore = metaStore;
    }

    @PostMapping
    public ResponseEntity<?> upload(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件为空"));
        }
        try {
            DocMetaEntity meta = ingestService.ingest(file);
            return ResponseEntity.ok(meta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    public List<DocMetaEntity> list() {
        return metaStore.findAll();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        int removed = ingestService.remove(id);
        if (removed == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("removedChunks", removed));
    }
}
