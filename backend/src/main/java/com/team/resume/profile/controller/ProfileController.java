package com.team.resume.profile.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;
import com.team.resume.profile.dto.ConfirmEvidenceRequest;
import com.team.resume.profile.service.EvidenceService;
import com.team.resume.profile.vo.ConfirmEvidenceResult;
import com.team.resume.profile.vo.EvidenceVO;

import jakarta.validation.Valid;

/**
 * 个人档案证据链接口（docs/api.md §6）：
 * GET  /api/profile/evidence          证据列表（默认 PENDING）
 * POST /api/profile/evidence/confirm  确认/拒绝抽取信息
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final EvidenceService evidenceService;

    public ProfileController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/evidence")
    public Result<List<EvidenceVO>> listEvidence(
            @AuthenticationPrincipal Long userId,
            @RequestParam(value = "status", required = false, defaultValue = "PENDING") String status) {
        return Result.ok(evidenceService.list(userId, status));
    }

    @PostMapping("/evidence/confirm")
    public Result<ConfirmEvidenceResult> confirm(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ConfirmEvidenceRequest request) {
        return Result.ok(evidenceService.confirm(userId, request));
    }
}
