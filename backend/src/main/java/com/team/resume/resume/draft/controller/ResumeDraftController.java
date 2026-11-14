package com.team.resume.resume.draft.controller;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.team.resume.ai.dto.SectionAnalysisResult;
import com.team.resume.common.Result;
import com.team.resume.resume.draft.dto.CompleteDraftRequest;
import com.team.resume.resume.draft.dto.CreateDraftRequest;
import com.team.resume.resume.draft.dto.SaveDraftRequest;
import com.team.resume.resume.draft.dto.SectionAnalyzeRequest;
import com.team.resume.resume.draft.dto.SectionChatRequest;
import com.team.resume.resume.draft.dto.SectionRewriteRequest;
import com.team.resume.resume.draft.dto.StarStructureRequest;
import com.team.resume.resume.draft.service.ResumeDraftService;
import com.team.resume.resume.draft.vo.DraftCompleteVO;
import com.team.resume.resume.draft.vo.ResumeDraftVO;
import com.team.resume.resume.edit.service.ResumeEditHistoryService;
import com.team.resume.resume.edit.vo.ResumeEditHistoryVO;

/**
 * 八步简历草稿接口（docs/2.0/api-v2.md §3）。
 * 草稿是可编辑态，生成后的 resume_version 不可变。
 */
@RestController
@RequestMapping("/api/resume-drafts")
public class ResumeDraftController {

    private final ResumeDraftService draftService;
    private final ResumeEditHistoryService editHistoryService;

    public ResumeDraftController(ResumeDraftService draftService,
                                 ResumeEditHistoryService editHistoryService) {
        this.draftService = draftService;
        this.editHistoryService = editHistoryService;
    }

    @PostMapping
    public Result<ResumeDraftVO> create(@AuthenticationPrincipal Long userId,
                                        @RequestBody(required = false) CreateDraftRequest request) {
        return Result.ok(draftService.create(userId, request));
    }

    @GetMapping
    public Result<List<ResumeDraftVO>> list(@AuthenticationPrincipal Long userId,
                                            @RequestParam(value = "status", required = false) String status) {
        return Result.ok(draftService.list(userId, status));
    }

    @GetMapping("/{id}")
    public Result<ResumeDraftVO> detail(@AuthenticationPrincipal Long userId,
                                        @PathVariable("id") Long id) {
        return Result.ok(draftService.detail(userId, id));
    }

    @PutMapping("/{id}")
    public Result<ResumeDraftVO> save(@AuthenticationPrincipal Long userId,
                                      @PathVariable("id") Long id,
                                      @RequestBody SaveDraftRequest request) {
        return Result.ok(draftService.save(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> abandon(@AuthenticationPrincipal Long userId,
                                @PathVariable("id") Long id) {
        draftService.abandon(userId, id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/complete")
    public Result<DraftCompleteVO> complete(@AuthenticationPrincipal Long userId,
                                            @PathVariable("id") Long id,
                                            @RequestBody(required = false) CompleteDraftRequest request) {
        return Result.ok(draftService.complete(userId, id, request));
    }

    @PostMapping("/{id}/sections/{sectionKey}/analyze")
    public Result<SectionAnalysisResult> analyze(@AuthenticationPrincipal Long userId,
                                                 @PathVariable("id") Long id,
                                                 @PathVariable("sectionKey") String sectionKey,
                                                 @RequestBody(required = false) SectionAnalyzeRequest request) {
        return Result.ok(editHistoryService.analyze(userId, id, sectionKey, request));
    }

    @PostMapping("/{id}/sections/{sectionKey}/rewrite")
    public Result<ResumeEditHistoryVO> rewrite(@AuthenticationPrincipal Long userId,
                                               @PathVariable("id") Long id,
                                               @PathVariable("sectionKey") String sectionKey,
                                               @RequestBody(required = false) SectionRewriteRequest request) {
        return Result.ok(editHistoryService.rewrite(userId, id, sectionKey, request));
    }

    @PostMapping("/{id}/sections/{sectionKey}/chat")
    public Result<ChatReply> chat(@AuthenticationPrincipal Long userId,
                                  @PathVariable("id") Long id,
                                  @PathVariable("sectionKey") String sectionKey,
                                  @RequestBody SectionChatRequest request) {
        return Result.ok(new ChatReply(editHistoryService.chat(userId, id, sectionKey, request), null));
    }

    @GetMapping("/{id}/edit-history")
    public Result<List<ResumeEditHistoryVO>> editHistory(@AuthenticationPrincipal Long userId,
                                                          @PathVariable("id") Long id) {
        return Result.ok(editHistoryService.listByDraft(userId, id));
    }

    @GetMapping("/{id}/experiences/{experienceId}/followups")
    public Result<JsonNode> followUps(@AuthenticationPrincipal Long userId,
                                      @PathVariable("id") Long id,
                                      @PathVariable("experienceId") String experienceId) {
        return Result.ok(draftService.experienceFollowUps(userId, id, experienceId));
    }

    @PostMapping("/{id}/experiences/{experienceId}/star")
    public Result<ResumeEditHistoryVO> structureStar(@AuthenticationPrincipal Long userId,
                                                     @PathVariable("id") Long id,
                                                     @PathVariable("experienceId") String experienceId,
                                                     @RequestBody StarStructureRequest request) {
        return Result.ok(editHistoryService.structureStar(userId, id, experienceId, request));
    }

    /** 区块对话回复；第一阶段不持久化对话。 */
    public record ChatReply(String reply, Long proposedEditHistoryId) {
    }
}
