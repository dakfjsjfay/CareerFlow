package com.team.resume.job.controller;

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
 * GET  /api/jobs            列表（分页 + keyword/city/salary 筛选）
 * GET  /api/jobs/{id}       详情（含技能要求）
 * POST /api/jobs            手动新增岗位
 * POST /api/jobs/{id}/parse AI 解析 JD
 * 岗位是公共资源，登录用户可读；写接口 MVP 不设管理端权限（内部使用）。
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
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String salary) {
        return Result.ok(jobService.list(page, size, keyword, city, salary));
    }

    @GetMapping("/{id}")
    public Result<JobDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(jobService.detail(id));
    }

    @PostMapping
    public Result<JobSummaryVO> create(@Valid @RequestBody CreateJobRequest request) {
        return Result.ok(jobService.create(request));
    }

    @PostMapping("/{id}/parse")
    public Result<JobParseVO> parse(@PathVariable("id") Long id,
                                    @RequestBody(required = false) ParseJdRequest request) {
        return Result.ok(jobService.parse(id, request == null ? new ParseJdRequest() : request));
    }
}
