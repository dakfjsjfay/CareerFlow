package com.team.resume.job.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.PageResult;
import com.team.resume.common.Result;
import com.team.resume.job.dto.CreateJobRequest;
import com.team.resume.job.dto.ParseJdRequest;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JobDetailVO;
import com.team.resume.job.vo.JobParseVO;
import com.team.resume.job.vo.JobSummaryVO;

import jakarta.validation.Valid;

/**
 * 岗位接口（docs/api.md §8）：
 * GET  /api/jobs            列表（分页 + keyword/city/salary 筛选，按可见性过滤）
 * GET  /api/jobs/{id}       详情（含技能要求与 canEdit）
 * POST /api/jobs            新增岗位（普通用户 → 私有；ADMIN → 公共）
 * POST /api/jobs/{id}/parse AI 解析 JD（仅 ADMIN 或岗位创建者）
 * 权限规则见 SecurityConfig：除登录/注册/健康检查外均需 JWT。
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public Result<PageResult<JobSummaryVO>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String salary,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String recruitmentBatch,
            @RequestParam(required = false) String recruitmentStatus) {
        return Result.ok(jobService.list(page, size, keyword, city, salary,
                companyId, recruitmentBatch, recruitmentStatus,
                currentUserId(authentication), isAdmin(authentication)));
    }

    @GetMapping("/{id}")
    public Result<JobDetailVO> detail(Authentication authentication, @PathVariable("id") Long id) {
        return Result.ok(jobService.detail(id, currentUserId(authentication), isAdmin(authentication)));
    }

    @PostMapping
    public Result<JobSummaryVO> create(Authentication authentication,
                                       @Valid @RequestBody CreateJobRequest request) {
        return Result.ok(jobService.create(request, currentUserId(authentication),
                isAdmin(authentication)));
    }

    @PostMapping("/{id}/parse")
    public Result<JobParseVO> parse(Authentication authentication,
                                    @PathVariable("id") Long id,
                                    @RequestBody(required = false) ParseJdRequest request) {
        return Result.ok(jobService.parse(id, request == null ? new ParseJdRequest() : request,
                currentUserId(authentication), isAdmin(authentication)));
    }

    /** JwtAuthFilter 把 userId 放进 principal，这里统一取出 */
    private static long currentUserId(Authentication authentication) {
        return Long.parseLong(String.valueOf(authentication.getPrincipal()));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
