package com.team.resume.application.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.application.dto.CreateApplicationRequest;
import com.team.resume.application.dto.FollowUpRequest;
import com.team.resume.application.dto.UpdateApplicationStatusRequest;
import com.team.resume.application.service.ApplicationService;
import com.team.resume.application.vo.ApplicationHistoryVO;
import com.team.resume.application.vo.ApplicationVO;
import com.team.resume.application.vo.FollowUpListVO;
import com.team.resume.common.PageResult;
import com.team.resume.common.Result;

import jakarta.validation.Valid;

import java.util.List;

/**
 * 投递管理接口（docs/api.md §11）：
 * POST   /api/applications                    模拟一键投递
 * GET    /api/applications                    投递列表（分页 + status 筛选）
 * GET    /api/applications/follow-ups         待跟进列表（主动提醒）
 * GET    /api/applications/{id}               投递详情
 * PUT    /api/applications/{id}/status        更新状态（后端状态机校验）
 * GET    /api/applications/{id}/history       状态历史（时间轴）
 * POST   /api/applications/{id}/follow-up     标记已跟进
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public Result<ApplicationVO> create(@AuthenticationPrincipal Long userId,
                                        @Valid @RequestBody CreateApplicationRequest request) {
        return Result.ok(applicationService.create(userId, request));
    }

    @GetMapping
    public Result<PageResult<ApplicationVO>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return Result.ok(applicationService.list(userId, page, size, status));
    }

    @GetMapping("/follow-ups")
    public Result<FollowUpListVO> followUps(@AuthenticationPrincipal Long userId) {
        return Result.ok(applicationService.followUps(userId));
    }

    @GetMapping("/{id}")
    public Result<ApplicationVO> detail(@AuthenticationPrincipal Long userId,
                                        @PathVariable("id") Long id) {
        return Result.ok(applicationService.detail(userId, id));
    }

    @PutMapping("/{id}/status")
    public Result<ApplicationVO> updateStatus(@AuthenticationPrincipal Long userId,
                                              @PathVariable("id") Long id,
                                              @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return Result.ok(applicationService.updateStatus(userId, id, request));
    }

    @GetMapping("/{id}/history")
    public Result<List<ApplicationHistoryVO>> history(@AuthenticationPrincipal Long userId,
                                                      @PathVariable("id") Long id) {
        return Result.ok(applicationService.history(userId, id));
    }

    @PostMapping("/{id}/follow-up")
    public Result<ApplicationVO> handleFollowUp(@AuthenticationPrincipal Long userId,
                                                @PathVariable("id") Long id,
                                                @Valid @RequestBody FollowUpRequest request) {
        return Result.ok(applicationService.handleFollowUp(userId, id, request));
    }
}
