package com.team.resume.analytics.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 反馈分析聚合结果（docs/requirements.md P1：投递漏斗 + 版本效果 + 偏好反馈）。
 * 所有 rate 均为 0~1 的小数，由前端格式化为百分比。
 */
@Data
@AllArgsConstructor
public class FeedbackAnalyticsVO {

    private Summary summary;

    private List<FunnelStage> funnel;

    private List<VersionEffect> versionEffects;

    private List<FeedbackDistribution> feedbackDistribution;

    @Data
    @AllArgsConstructor
    public static class Summary {

        private long totalApplications;

        private long readCount;

        private long interviewCount;

        private long offerCount;

        private long feedbackCount;

        private double readRate;

        private double interviewRate;

        private double offerRate;
    }

    @Data
    @AllArgsConstructor
    public static class FunnelStage {

        /** APPLIED / READ / INTERVIEW / OFFER */
        private String stage;

        private String label;

        private long count;

        private double rate;
    }

    @Data
    @AllArgsConstructor
    public static class VersionEffect {

        private Long resumeVersionId;

        private Integer versionNumber;

        private long applicationCount;

        private long readCount;

        private long interviewCount;

        private long offerCount;

        private double readRate;

        private double interviewRate;

        private double offerRate;
    }

    @Data
    @AllArgsConstructor
    public static class FeedbackDistribution {

        private String feedbackType;

        private String label;

        private long count;

        private double rate;
    }
}
