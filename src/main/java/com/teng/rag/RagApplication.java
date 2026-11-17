package com.teng.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * RAG 知识库问答服务入口。
 *
 * <p>管线：文档上传 → 切分 → BGE 中文嵌入 → 向量存储；
 * 提问时：查询嵌入 → 相似检索 → 拼装上下文 Prompt → LLM 流式生成 + 引用溯源。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
