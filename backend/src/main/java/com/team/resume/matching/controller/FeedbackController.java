package com.team.resume.matching.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.Result;
import com.team.resume.matching.dto.FeedbackRequest;
import com.team.resume.matching.service.FeedbackService;
import com.team.resume.matching.vo.FeedbackVO;

import jakarta.validation.Valid;

/**
 * 偏好反馈接口（docs/api.md §12）：POST /api/feedback。
 * 反馈写入 user_feedback 后，岗位推荐按后置规则调整排序。
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    public Result<FeedbackVO> submit(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody FeedbackRequest request) {
        return Result.ok(feedbackService.submit(userId, request));
    }
}
