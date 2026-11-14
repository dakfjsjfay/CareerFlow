package com.team.resume.analytics.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.analytics.service.FeedbackAnalyticsService;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO;
import com.team.resume.common.Result;

/**
 * 反馈分析接口（P1）：
 * GET /api/analytics/feedback 投递漏斗 + 简历版本效果 + 偏好反馈分布。
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final FeedbackAnalyticsService feedbackAnalyticsService;

    public AnalyticsController(FeedbackAnalyticsService feedbackAnalyticsService) {
        this.feedbackAnalyticsService = feedbackAnalyticsService;
    }

    @GetMapping("/feedback")
    public Result<FeedbackAnalyticsVO> feedback(@AuthenticationPrincipal Long userId) {
        return Result.ok(feedbackAnalyticsService.analyze(userId));
    }
}
