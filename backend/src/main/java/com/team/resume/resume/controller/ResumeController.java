package com.team.resume.resume.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;
import com.team.resume.resume.dto.CreateResumeRequest;
import com.team.resume.resume.service.ResumeService;
import com.team.resume.resume.vo.ResumeDetailVO;
import com.team.resume.resume.vo.ResumeSummaryVO;
import com.team.resume.resume.vo.ResumeVersionSummaryVO;
import com.team.resume.resume.vo.ResumeVersionVO;

/**
 * 简历接口（docs/api.md §7）：创建/列表/详情/版本。
 * 生成、手动保存与导出在后续任务追加。
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public Result<ResumeSummaryVO> create(@AuthenticationPrincipal Long userId,
                                          @RequestBody(required = false) CreateResumeRequest request) {
        return Result.ok(resumeService.create(userId, request));
    }

    @GetMapping
    public Result<List<ResumeSummaryVO>> list(@AuthenticationPrincipal Long userId) {
        return Result.ok(resumeService.list(userId));
    }

    @GetMapping("/{id}")
    public Result<ResumeDetailVO> detail(@AuthenticationPrincipal Long userId,
                                         @PathVariable("id") Long id) {
        return Result.ok(resumeService.detail(userId, id));
    }

    @GetMapping("/{id}/versions")
    public Result<List<ResumeVersionSummaryVO>> versions(@AuthenticationPrincipal Long userId,
                                                         @PathVariable("id") Long id) {
        return Result.ok(resumeService.listVersions(userId, id));
    }

    @GetMapping("/{id}/versions/{versionId}")
    public Result<ResumeVersionVO> version(@AuthenticationPrincipal Long userId,
                                           @PathVariable("id") Long id,
                                           @PathVariable("versionId") Long versionId) {
        return Result.ok(resumeService.getVersion(userId, id, versionId));
    }
}
