package com.team.resume.resume.edit.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;
import com.team.resume.resume.edit.dto.ConfirmEditRequest;
import com.team.resume.resume.edit.service.ResumeEditHistoryService;
import com.team.resume.resume.edit.vo.ResumeEditHistoryVO;

/**
 * 候选修改确认/拒绝与修改历史查询（docs/2.0/api-v2.md §4.4-§4.7）。
 * 未确认的 AI 候选不会写进草稿，更不会进入正式版本。
 */
@RestController
public class ResumeEditHistoryController {

    private final ResumeEditHistoryService editHistoryService;

    public ResumeEditHistoryController(ResumeEditHistoryService editHistoryService) {
        this.editHistoryService = editHistoryService;
    }

    @GetMapping("/api/resume-edit-history/{id}")
    public Result<ResumeEditHistoryVO> detail(@AuthenticationPrincipal Long userId,
                                              @PathVariable("id") Long id) {
        return Result.ok(editHistoryService.detail(userId, id));
    }

    @PostMapping("/api/resume-edit-history/{id}/confirm")
    public Result<ResumeEditHistoryVO> confirm(@AuthenticationPrincipal Long userId,
                                               @PathVariable("id") Long id,
                                               @RequestBody(required = false) ConfirmEditRequest request) {
        return Result.ok(editHistoryService.confirm(userId, id, request));
    }

    @PostMapping("/api/resume-edit-history/{id}/reject")
    public Result<ResumeEditHistoryVO> reject(@AuthenticationPrincipal Long userId,
                                              @PathVariable("id") Long id) {
        return Result.ok(editHistoryService.reject(userId, id));
    }

    @GetMapping("/api/resumes/{resumeId}/edit-history")
    public Result<List<ResumeEditHistoryVO>> listByResume(@AuthenticationPrincipal Long userId,
                                                          @PathVariable("resumeId") Long resumeId) {
        return Result.ok(editHistoryService.listByResume(userId, resumeId));
    }
}
