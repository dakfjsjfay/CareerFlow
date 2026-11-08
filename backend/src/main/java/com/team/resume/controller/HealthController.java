package com.team.resume.controller;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;

/**
 * 健康检查：用于 Day 1 验收（服务可访问 + 数据库可连接）。
 * database 字段真实探测连接池，避免「服务能起但库连不上」被误判为健康。
 */
@RestController
public class HealthController {

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "careerflow-backend");
        body.put("database", checkDatabase() ? "UP" : "DOWN");
        return Result.ok(body);
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }
}
