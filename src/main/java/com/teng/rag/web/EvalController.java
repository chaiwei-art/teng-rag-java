package com.teng.rag.web;

import com.teng.rag.eval.EvalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测接口：POST /api/eval/run → 跑离线检索评测，返回召回报告。
 * 纯检索层、不调 LLM：零 token 成本，可反复运行对比参数调整效果。
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    @PostMapping("/run")
    public EvalService.EvalReport run() {
        return evalService.run();
    }
}
