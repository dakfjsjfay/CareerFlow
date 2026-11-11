package com.team.resume.matching.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.PageResult;
import com.team.resume.common.Result;
import com.team.resume.matching.dto.AnalyzeRequest;
import com.team.resume.matching.service.MatchService;
import com.team.resume.matching.vo.MatchAnalyzeVO;
import com.team.resume.matching.vo.MatchResultVO;
import com.team.resume.matching.vo.RecommendItemVO;

import jakarta.validation.Valid;

/**
 * 匹配接口（docs/api.md §9）：
 * POST /api/match/analyze          分析匹配度（落 match_result + match_detail）
 * GET  /api/match/{id}            匹配详情（归属校验）
 * GET  /api/match/jobs/recommend  岗位推荐（反馈后置规则排序）
 */
@RestController
@RequestMapping("/api/match")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping("/analyze")
    public Result<MatchAnalyzeVO> analyze(@AuthenticationPrincipal Long userId,
                                          @Valid @RequestBody AnalyzeRequest request) {
        return Result.ok(matchService.analyze(userId, request));
    }

    @GetMapping("/{id}")
    public Result<MatchResultVO> detail(@AuthenticationPrincipal Long userId,
                                        @PathVariable("id") Long id) {
        return Result.ok(matchService.detail(userId, id));
    }

    @GetMapping("/jobs/recommend")
    public Result<PageResult<RecommendItemVO>> recommend(@AuthenticationPrincipal Long userId,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(defaultValue = "10") long size) {
        return Result.ok(matchService.recommend(userId, page, size));
    }
}
