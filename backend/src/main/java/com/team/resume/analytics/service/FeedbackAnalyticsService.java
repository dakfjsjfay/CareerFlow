package com.team.resume.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO.FeedbackDistribution;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO.FunnelStage;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO.Summary;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO.VersionEffect;
import com.team.resume.application.entity.ApplicationHistory;
import com.team.resume.application.entity.JobApplication;
import com.team.resume.application.mapper.ApplicationHistoryMapper;
import com.team.resume.application.mapper.JobApplicationMapper;
import com.team.resume.matching.entity.UserFeedback;
import com.team.resume.matching.mapper.UserFeedbackMapper;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeVersionMapper;

/**
 * 反馈分析（P1）：投递漏斗、简历版本效果、偏好反馈分布。
 *
 * 漏斗与版本效果按“历史到达过的最高阶段”统计：
 * 状态流转会写 application_history，因此被拒绝的记录仍能追溯到曾到达的已读/面试阶段。
 * 全部查询按 user_id 隔离。
 */
@Service
public class FeedbackAnalyticsService {

    private static final int STAGE_APPLIED = 1;
    private static final int STAGE_READ = 2;
    private static final int STAGE_INTERVIEW = 3;
    private static final int STAGE_OFFER = 4;

    private static final Map<String, String> FEEDBACK_LABELS = createFeedbackLabels();

    private final JobApplicationMapper applicationMapper;
    private final ApplicationHistoryMapper historyMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final UserFeedbackMapper feedbackMapper;

    public FeedbackAnalyticsService(JobApplicationMapper applicationMapper,
                                    ApplicationHistoryMapper historyMapper,
                                    ResumeVersionMapper resumeVersionMapper,
                                    UserFeedbackMapper feedbackMapper) {
        this.applicationMapper = applicationMapper;
        this.historyMapper = historyMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.feedbackMapper = feedbackMapper;
    }

    public FeedbackAnalyticsVO analyze(Long userId) {
        List<JobApplication> applications = applicationMapper.selectList(
                new LambdaQueryWrapper<JobApplication>().eq(JobApplication::getUserId, userId));
        if (applications == null) {
            applications = List.of();
        }

        Map<Long, Integer> maxStages = resolveMaxStages(applications);
        long totalApplications = applications.size();
        long readCount = countReached(maxStages, STAGE_READ);
        long interviewCount = countReached(maxStages, STAGE_INTERVIEW);
        long offerCount = countReached(maxStages, STAGE_OFFER);

        List<UserFeedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<UserFeedback>().eq(UserFeedback::getUserId, userId));
        if (feedbacks == null) {
            feedbacks = List.of();
        }

        Summary summary = new Summary(
                totalApplications,
                readCount,
                interviewCount,
                offerCount,
                feedbacks.size(),
                ratio(readCount, totalApplications),
                ratio(interviewCount, totalApplications),
                ratio(offerCount, totalApplications));

        return new FeedbackAnalyticsVO(
                summary,
                buildFunnel(totalApplications, readCount, interviewCount, offerCount),
                buildVersionEffects(applications, maxStages),
                buildFeedbackDistribution(feedbacks));
    }

    private Map<Long, Integer> resolveMaxStages(List<JobApplication> applications) {
        Map<Long, Integer> maxStages = new HashMap<>();
        if (applications.isEmpty()) {
            return maxStages;
        }

        List<Long> applicationIds = applications.stream()
                .map(JobApplication::getId)
                .toList();
        for (JobApplication application : applications) {
            maxStages.put(application.getId(), stageRank(application.getStatus()));
        }

        List<ApplicationHistory> histories = historyMapper.selectList(
                new LambdaQueryWrapper<ApplicationHistory>()
                        .in(ApplicationHistory::getApplicationId, applicationIds));
        if (histories == null) {
            return maxStages;
        }
        for (ApplicationHistory history : histories) {
            int reached = Math.max(stageRank(history.getOldStatus()), stageRank(history.getNewStatus()));
            maxStages.merge(history.getApplicationId(), reached, Math::max);
        }
        return maxStages;
    }

    private static int stageRank(String status) {
        if (status == null) {
            return 0;
        }
        return switch (status) {
            case "APPLIED", "FAILED", "WITHDRAWN", "NO_RESPONSE" -> STAGE_APPLIED;
            case "READ" -> STAGE_READ;
            case "INTERVIEW_INVITED", "INTERVIEWING" -> STAGE_INTERVIEW;
            case "OFFERED" -> STAGE_OFFER;
            default -> 0;
        };
    }

    private static long countReached(Map<Long, Integer> maxStages, int stage) {
        return maxStages.values().stream()
                .filter(reached -> reached >= stage)
                .count();
    }

    private static List<FunnelStage> buildFunnel(long total, long read, long interview, long offer) {
        return List.of(
                new FunnelStage("APPLIED", "已投递", total, ratio(total, total)),
                new FunnelStage("READ", "已读", read, ratio(read, total)),
                new FunnelStage("INTERVIEW", "面试", interview, ratio(interview, total)),
                new FunnelStage("OFFER", "录用", offer, ratio(offer, total)));
    }

    private List<VersionEffect> buildVersionEffects(List<JobApplication> applications,
                                                    Map<Long, Integer> maxStages) {
        if (applications.isEmpty()) {
            return List.of();
        }

        Set<Long> versionIds = applications.stream()
                .map(JobApplication::getResumeVersionId)
                .collect(Collectors.toSet());
        Map<Long, ResumeVersion> versions = new HashMap<>();
        List<ResumeVersion> loaded = resumeVersionMapper.selectBatchIds(versionIds);
        if (loaded != null) {
            for (ResumeVersion version : loaded) {
                versions.put(version.getId(), version);
            }
        }

        Map<Long, List<JobApplication>> grouped = applications.stream()
                .collect(Collectors.groupingBy(JobApplication::getResumeVersionId));

        List<VersionEffect> result = new ArrayList<>();
        for (Map.Entry<Long, List<JobApplication>> entry : grouped.entrySet()) {
            long applicationCount = entry.getValue().size();
            long readCount = entry.getValue().stream()
                    .filter(application -> maxStages.getOrDefault(application.getId(), 0) >= STAGE_READ)
                    .count();
            long interviewCount = entry.getValue().stream()
                    .filter(application -> maxStages.getOrDefault(application.getId(), 0) >= STAGE_INTERVIEW)
                    .count();
            long offerCount = entry.getValue().stream()
                    .filter(application -> maxStages.getOrDefault(application.getId(), 0) >= STAGE_OFFER)
                    .count();
            ResumeVersion version = versions.get(entry.getKey());
            result.add(new VersionEffect(
                    entry.getKey(),
                    version == null ? null : version.getVersionNumber(),
                    applicationCount,
                    readCount,
                    interviewCount,
                    offerCount,
                    ratio(readCount, applicationCount),
                    ratio(interviewCount, applicationCount),
                    ratio(offerCount, applicationCount)));
        }

        result.sort(Comparator
                .comparingInt((VersionEffect effect) ->
                        effect.getVersionNumber() == null ? 0 : effect.getVersionNumber())
                .reversed()
                .thenComparing(VersionEffect::getResumeVersionId, Comparator.reverseOrder()));
        return result;
    }

    private static List<FeedbackDistribution> buildFeedbackDistribution(List<UserFeedback> feedbacks) {
        Map<String, Long> counts = new LinkedHashMap<>();
        FEEDBACK_LABELS.keySet().forEach(type -> counts.put(type, 0L));
        for (UserFeedback feedback : feedbacks) {
            String type = feedback.getFeedbackType() == null ? "UNKNOWN" : feedback.getFeedbackType();
            counts.merge(type, 1L, Long::sum);
        }

        long total = feedbacks.size();
        return counts.entrySet().stream()
                .map(entry -> new FeedbackDistribution(
                        entry.getKey(),
                        FEEDBACK_LABELS.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue(),
                        ratio(entry.getValue(), total)))
                .toList();
    }

    private static Map<String, String> createFeedbackLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("NOT_INTERESTED", "不感兴趣");
        labels.put("LOCATION_MISMATCH", "地点不符");
        labels.put("SALARY_MISMATCH", "薪资不符");
        labels.put("SKILL_ALREADY_HAVE", "技能已掌握");
        return labels;
    }

    private static double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
