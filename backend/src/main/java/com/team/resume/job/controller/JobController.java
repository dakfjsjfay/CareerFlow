package com.team.resume.job.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.PageVO;
import com.team.resume.common.Result;
import com.team.resume.job.dto.JdParseRequest;
import com.team.resume.job.dto.JobCreateRequest;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JdParseResultVO;
import com.team.resume.job.vo.JobDetailVO;
import com.team.resume.job.vo.JobSummaryVO;

import jakarta.validation.Valid;

/**
 * 岗位接口（docs/api.md §8）：
 * GET /api/jobs 列表、GET /api/jobs/{id} 详情、
 * POST /api/jobs 手动新增（开发用途）、POST /api/jobs/{id}/parse 解析 JD。
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping
    public Result<PageVO<JobSummaryVO>> list(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "salary", required = false) String salary) {
        return Result.ok(jobService.list(page, size, keyword, city, salary));
    }

    @GetMapping("/{id}")
    public Result<JobDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(jobService.detail(id));
    }

    @PostMapping
    public Result<JobDetailVO> create(@Valid @RequestBody JobCreateRequest request) {
        return Result.ok(jobService.create(request));
    }

    @PostMapping("/{id}/parse")
    public Result<JdParseResultVO> parse(@PathVariable("id") Long id,
                                         @Valid @RequestBody(required = false) JdParseRequest request) {
        return Result.ok(jobService.parse(id, request));
    }
}
