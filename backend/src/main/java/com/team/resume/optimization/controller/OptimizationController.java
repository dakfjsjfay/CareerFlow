package com.team.resume.optimization.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;
import com.team.resume.optimization.dto.CreateOptimizationTaskRequest;
import com.team.resume.optimization.dto.ReplyOptimizationRequest;
import com.team.resume.optimization.service.OptimizationService;
import com.team.resume.optimization.vo.OptimizationConfirmVO;
import com.team.resume.optimization.vo.OptimizationReplyVO;
import com.team.resume.optimization.vo.OptimizationTaskVO;

import jakarta.validation.Valid;

/**
 * 反向优化接口（docs/api.md §10）：
 * POST /api/optimization/tasks            创建优化任务（点击差距项发起）
 * GET  /api/optimization/tasks/{id}       任务详情（归属校验）
 * POST /api/optimization/tasks/{id}/reply 回复 AI 追问（多轮）
 * POST /api/optimization/tasks/{id}/confirm 确认补充信息 → 写档案 → 重生成简历
 */
@RestController
@RequestMapping("/api/optimization")
public class OptimizationController {

    private final OptimizationService optimizationService;

    public OptimizationController(OptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @PostMapping("/tasks")
    public Result<OptimizationTaskVO> create(@AuthenticationPrincipal Long userId,
                                             @Valid @RequestBody CreateOptimizationTaskRequest request) {
        return Result.ok(optimizationService.create(userId, request));
    }

    @GetMapping("/tasks/{id}")
    public Result<OptimizationTaskVO> detail(@AuthenticationPrincipal Long userId,
                                             @PathVariable("id") Long taskId) {
        return Result.ok(optimizationService.detail(userId, taskId));
    }

    @PostMapping("/tasks/{id}/reply")
    public Result<OptimizationReplyVO> reply(@AuthenticationPrincipal Long userId,
                                             @PathVariable("id") Long taskId,
                                             @Valid @RequestBody ReplyOptimizationRequest request) {
        return Result.ok(optimizationService.reply(userId, taskId, request));
    }

    @PostMapping("/tasks/{id}/confirm")
    public Result<OptimizationConfirmVO> confirm(@AuthenticationPrincipal Long userId,
                                                 @PathVariable("id") Long taskId) {
        return Result.ok(optimizationService.confirm(userId, taskId));
    }
}
