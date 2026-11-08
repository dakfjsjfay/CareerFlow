package com.team.resume.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;

/**
 * 健康检查：用于 Day 1 验收（服务可访问）。
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "careerflow-backend");
        return Result.ok(body);
    }
}
