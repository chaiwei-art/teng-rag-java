package com.teng.rag;

import com.teng.rag.config.RedisBootstrap;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * RAG 知识库问答服务入口。
 *
 * <p>管线：文档上传 → 切分 → BGE 中文嵌入 → 向量存储；
 * 提问时：缓存查询 → 相似检索 → 拼装上下文 Prompt → LLM 流式生成 + 引用溯源 → 历史落库。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagApplication {

    public static void main(String[] args) {
        // Spring 容器启动前确保本机 Redis 就绪（没有则拉起内嵌实例，失败则缓存层降级）
        RedisBootstrap.ensureLocalRedis();
        SpringApplication.run(RagApplication.class, args);
    }
}
