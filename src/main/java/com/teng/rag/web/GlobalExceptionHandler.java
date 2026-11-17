package com.teng.rag.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 统一异常响应：不让堆栈裸奔给前端。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage() == null ? "参数错误" : e.getMessage()));
    }

    /** 路径不存在：不要吞成500（Spring 6 的 NoResourceFoundException 也是 Exception） */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(
            org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internal(Exception e) {
        return ResponseEntity.internalServerError()
                .body(Map.of("message", e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage())));
    }
}
