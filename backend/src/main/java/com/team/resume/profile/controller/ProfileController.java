package com.team.resume.profile.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;
import com.team.resume.profile.dto.ConfirmEvidenceRequest;
import com.team.resume.profile.dto.ExperienceRequest;
import com.team.resume.profile.dto.ProfileUpdateRequest;
import com.team.resume.profile.dto.ProjectRequest;
import com.team.resume.profile.service.CompletenessService;
import com.team.resume.profile.service.EvidenceService;
import com.team.resume.profile.service.ProfileExperienceService;
import com.team.resume.profile.service.ProfileProjectService;
import com.team.resume.profile.service.ProfileService;
import com.team.resume.profile.vo.CompletenessVO;
import com.team.resume.profile.vo.ConfirmEvidenceResult;
import com.team.resume.profile.vo.EvidenceVO;
import com.team.resume.profile.vo.ProfileVO;

import jakarta.validation.Valid;

/**
 * 个人档案接口（docs/api.md §6）：
 * GET/PUT /api/profile、项目/经历 CRUD、证据列表与确认、完整度。
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileProjectService projectService;
    private final ProfileExperienceService experienceService;
    private final EvidenceService evidenceService;
    private final CompletenessService completenessService;

    public ProfileController(ProfileService profileService,
                             ProfileProjectService projectService,
                             ProfileExperienceService experienceService,
                             EvidenceService evidenceService,
                             CompletenessService completenessService) {
        this.profileService = profileService;
        this.projectService = projectService;
        this.experienceService = experienceService;
        this.evidenceService = evidenceService;
        this.completenessService = completenessService;
    }

    @GetMapping
    public Result<ProfileVO> getProfile(@AuthenticationPrincipal Long userId) {
        return Result.ok(profileService.getProfile(userId));
    }

    @PutMapping
    public Result<ProfileVO> updateProfile(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody ProfileUpdateRequest request) {
        return Result.ok(profileService.updateProfile(userId, request));
    }

    @PostMapping("/projects")
    public Result<ProfileVO.ProjectVO> createProject(@AuthenticationPrincipal Long userId,
                                                     @Valid @RequestBody ProjectRequest request) {
        return Result.ok(projectService.create(userId, request));
    }

    @PutMapping("/projects/{id}")
    public Result<ProfileVO.ProjectVO> updateProject(@AuthenticationPrincipal Long userId,
                                                     @PathVariable("id") Long projectId,
                                                     @Valid @RequestBody ProjectRequest request) {
        return Result.ok(projectService.update(userId, projectId, request));
    }

    @DeleteMapping("/projects/{id}")
    public Result<Void> deleteProject(@AuthenticationPrincipal Long userId,
                                      @PathVariable("id") Long projectId) {
        projectService.delete(userId, projectId);
        return Result.ok();
    }

    @PostMapping("/experiences")
    public Result<ProfileVO.ExperienceVO> createExperience(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ExperienceRequest request) {
        return Result.ok(experienceService.create(userId, request));
    }

    @PutMapping("/experiences/{id}")
    public Result<ProfileVO.ExperienceVO> updateExperience(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long experienceId,
            @Valid @RequestBody ExperienceRequest request) {
        return Result.ok(experienceService.update(userId, experienceId, request));
    }

    @DeleteMapping("/experiences/{id}")
    public Result<Void> deleteExperience(@AuthenticationPrincipal Long userId,
                                         @PathVariable("id") Long experienceId) {
        experienceService.delete(userId, experienceId);
        return Result.ok();
    }

    @GetMapping("/completeness")
    public Result<CompletenessVO> completeness(@AuthenticationPrincipal Long userId) {
        return Result.ok(completenessService.get(userId));
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
