package com.team.resume.matching.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.resume.application.ApplicationStatus;
import com.team.resume.application.entity.JobApplication;
import com.team.resume.application.mapper.JobApplicationMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.common.PageResult;
import com.team.resume.job.entity.Job;
import com.team.resume.job.entity.JobRequirement;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.mapper.JobRequirementMapper;
import com.team.resume.matching.dto.AnalyzeRequest;
import com.team.resume.matching.entity.MatchDetail;
import com.team.resume.matching.entity.MatchResult;
import com.team.resume.matching.entity.UserFeedback;
import com.team.resume.matching.mapper.MatchDetailMapper;
import com.team.resume.matching.mapper.MatchResultMapper;
import com.team.resume.matching.mapper.UserFeedbackMapper;
import com.team.resume.matching.service.MatchCalculator.Result;
import com.team.resume.matching.service.MatchCalculator.SkillMatch;
import com.team.resume.matching.vo.MatchAnalyzeVO;
import com.team.resume.matching.vo.MatchDetailVO;
import com.team.resume.matching.vo.MatchResultVO;
import com.team.resume.matching.vo.RecommendItemVO;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.service.ProfileDataService;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;

/**
 * 匹配业务（docs/api.md §9）：
 * - analyze：技能矩阵 + 加权评分，落 match_result + match_detail（关联简历版本）
 * - detail：按用户归属校验后返回逐技能详情
 * - recommend：用用户最新简历版本对全部岗位评分，叠加用户反馈后置规则
 *   与同类岗位历史已读率/面试率微调（可解释统计规则，非机器学习）
 */
@Service
public class MatchService {

    private final JobMapper jobMapper;
    private final JobRequirementMapper requirementMapper;
    private final ProfileDataService profileDataService;
    private final ProfileProjectMapper projectMapper;
    private final ProfileEvidenceMapper evidenceMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final MatchResultMapper matchResultMapper;
    private final MatchDetailMapper matchDetailMapper;
    private final UserFeedbackMapper feedbackMapper;
    private final JobApplicationMapper applicationMapper;

    public MatchService(JobMapper jobMapper, JobRequirementMapper requirementMapper,
                        ProfileDataService profileDataService, ProfileProjectMapper projectMapper,
                        ProfileEvidenceMapper evidenceMapper, ResumeMapper resumeMapper,
                        ResumeVersionMapper resumeVersionMapper, MatchResultMapper matchResultMapper,
                        MatchDetailMapper matchDetailMapper, UserFeedbackMapper feedbackMapper,
                        JobApplicationMapper applicationMapper) {
        this.jobMapper = jobMapper;
        this.requirementMapper = requirementMapper;
        this.profileDataService = profileDataService;
        this.projectMapper = projectMapper;
        this.evidenceMapper = evidenceMapper;
        this.resumeMapper = resumeMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.matchResultMapper = matchResultMapper;
        this.matchDetailMapper = matchDetailMapper;
        this.feedbackMapper = feedbackMapper;
        this.applicationMapper = applicationMapper;
    }

    @Transactional
    public MatchAnalyzeVO analyze(Long userId, AnalyzeRequest request) {
        Job job = requireJob(request.getJobId());
        ResumeVersion version = requireOwnedVersion(userId, request.getResumeVersionId());

        List<JobRequirement> requirements = listRequirements(job.getId());
        if (requirements.isEmpty()) {
            throw new BusinessException(400, "该岗位还没有技能要求，请先解析 JD");
        }

        Result calculated = calculate(userId, requirements);

        MatchResult matchResult = new MatchResult();
        matchResult.setResumeVersionId(version.getId());
        matchResult.setJobId(job.getId());
        matchResult.setMatchScore(calculated.score());
        matchResult.setCreatedAt(LocalDateTime.now());
        matchResultMapper.insert(matchResult);

        List<MatchDetailVO> detailVOs = new ArrayList<>();
        for (SkillMatch match : calculated.details()) {
            MatchDetail detail = new MatchDetail();
            detail.setMatchResultId(matchResult.getId());
            detail.setSkill(match.skill());
            detail.setMatchStatus(match.status());
            detail.setEvidence(match.evidence());
            detail.setSuggestion(match.suggestion());
            matchDetailMapper.insert(detail);
            detailVOs.add(toDetailVO(detail));
        }

        MatchAnalyzeVO vo = new MatchAnalyzeVO();
        vo.setMatchResultId(matchResult.getId());
        vo.setJobId(job.getId());
        vo.setResumeVersionId(version.getId());
        vo.setMatchScore(calculated.score());
        vo.setMatchedCount(calculated.matchedCount());
        vo.setPartialCount(calculated.partialCount());
        vo.setMissingCount(calculated.missingCount());
        vo.setUnconfirmedCount(calculated.unconfirmedCount());
        vo.setDetails(detailVOs);
        return vo;
    }

    public MatchResultVO detail(Long userId, Long matchResultId) {
        MatchResult result = matchResultMapper.selectById(matchResultId);
        if (result == null) {
            throw new BusinessException(404, "匹配结果不存在");
        }
        // 归属校验：match_result → resume_version → resume → user
        ResumeVersion version = resumeVersionMapper.selectById(result.getResumeVersionId());
        if (version == null) {
            throw new BusinessException(404, "关联的简历版本不存在");
        }
        Resume resume = resumeMapper.selectById(version.getResumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(403, "没有访问权限");
        }

        List<MatchDetailVO> details = matchDetailMapper.selectList(
                        new LambdaQueryWrapper<MatchDetail>()
                                .eq(MatchDetail::getMatchResultId, matchResultId)
                                .orderByAsc(MatchDetail::getId))
                .stream()
                .map(this::toDetailVO)
                .toList();

        MatchResultVO vo = new MatchResultVO();
        vo.setMatchResultId(result.getId());
        vo.setJobId(result.getJobId());
        vo.setResumeVersionId(result.getResumeVersionId());
        vo.setMatchScore(result.getMatchScore());
        vo.setDetails(details);
        return vo;
    }

    public PageResult<RecommendItemVO> recommend(Long userId, long page, long size) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);

        ResumeVersion version = findLatestVersion(userId);
        if (version == null) {
            throw new BusinessException(400, "请先生成一份简历版本，再查看岗位推荐");
        }

        List<Job> jobs = jobMapper.selectList(null);
        if (jobs.isEmpty()) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }

        List<Long> jobIds = jobs.stream().map(Job::getId).toList();
        Map<Long, List<JobRequirement>> requirementMap = requirementMapper.selectList(
                        new LambdaQueryWrapper<JobRequirement>().in(JobRequirement::getJobId, jobIds))
                .stream()
                .collect(Collectors.groupingBy(JobRequirement::getJobId));

        Result profileResult = calculate(userId, List.of());
        List<UserFeedback> feedbacks = feedbackMapper.selectList(
                new LambdaQueryWrapper<UserFeedback>().eq(UserFeedback::getUserId, userId));
        FeedbackRules rules = new FeedbackRules(feedbacks, jobs);

        Map<Long, Job> jobById = jobs.stream().collect(Collectors.toMap(Job::getId, j -> j));
        List<JobApplication> applications = applicationMapper.selectList(
                new LambdaQueryWrapper<JobApplication>().eq(JobApplication::getUserId, userId));
        Map<String, HistoryStats> historyByPosition = HistoryStats.group(applications, jobById);

        List<RecommendItemVO> items = new ArrayList<>();
        for (Job job : jobs) {
            List<JobRequirement> requirements = requirementMap.getOrDefault(job.getId(), List.of());
            if (requirements.isEmpty()) {
                continue; // 未解析技能要求的岗位不参与推荐
            }
            Result r = calculate(userId, requirements);
            RecommendItemVO item = new RecommendItemVO();
            item.setJobId(job.getId());
            item.setCompany(job.getCompany());
            item.setPosition(job.getPosition());
            item.setCity(job.getCity());
            item.setSalary(job.getSalary());
            item.setMatchScore(r.score());
            item.setRankReasons(new ArrayList<>());
            items.add(item);
        }

        // 用户反馈后置规则 + 同类岗位历史反馈统计（可解释权重，非机器学习）
        for (RecommendItemVO item : items) {
            BigDecimal adjusted = item.getMatchScore();
            BigDecimal factor = rules.factorFor(item);
            if (factor.compareTo(BigDecimal.ONE) < 0) {
                adjusted = adjusted.multiply(factor);
                item.getRankReasons().addAll(rules.reasonsFor(item));
            }

            HistoryStats stats = historyByPosition.get(normalizePosition(item.getPosition()));
            if (stats != null) {
                BigDecimal historyFactor = stats.factor();
                if (historyFactor.compareTo(BigDecimal.ONE) > 0) {
                    adjusted = adjusted.multiply(historyFactor);
                    item.getRankReasons().add(stats.reason());
                }
            }
            item.setAdjustedScore(adjusted);
        }

        items.sort(Comparator.comparing(RecommendItemVO::getAdjustedScore,
                Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Comparator.comparing(RecommendItemVO::getJobId, Comparator.reverseOrder())));

        int from = (int) Math.min((safePage - 1) * safeSize, items.size());
        int to = (int) Math.min(from + safeSize, items.size());
        List<RecommendItemVO> pageItems = items.subList(from, to);
        return new PageResult<>(pageItems, items.size(), safePage, safeSize);
    }

    // ---------- 内部工具 ----------

    private Result calculate(Long userId, List<JobRequirement> requirements) {
        UserProfile profile = profileDataService.findByUserId(userId);
        List<String> skills = profile != null && profile.getSkills() != null
                ? profile.getSkills() : List.of();
        List<ProfileProject> projects = profile != null
                ? projectMapper.selectList(new LambdaQueryWrapper<ProfileProject>()
                        .eq(ProfileProject::getProfileId, profile.getId()))
                : List.of();
        List<ProfileEvidence> pending = evidenceMapper.selectList(
                new LambdaQueryWrapper<ProfileEvidence>()
                        .eq(ProfileEvidence::getUserId, userId)
                        .eq(ProfileEvidence::getConfirmationStatus, "PENDING"));
        return MatchCalculator.calculate(requirements, skills, projects, pending);
    }

    private ResumeVersion findLatestVersion(Long userId) {
        List<Resume> resumes = resumeMapper.selectList(
                new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
        if (resumes.isEmpty()) {
            return null;
        }
        List<Long> resumeIds = resumes.stream().map(Resume::getId).toList();
        return resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .in(ResumeVersion::getResumeId, resumeIds)
                        .orderByDesc(ResumeVersion::getCreatedAt)
                        .orderByDesc(ResumeVersion::getId)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private ResumeVersion requireOwnedVersion(Long userId, Long versionId) {
        ResumeVersion version = resumeVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        Resume resume = resumeMapper.selectById(version.getResumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(403, "该简历版本不属于当前用户");
        }
        return version;
    }

    private Job requireJob(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return job;
    }

    private List<JobRequirement> listRequirements(Long jobId) {
        return requirementMapper.selectList(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getJobId, jobId)
                .orderByDesc(JobRequirement::getWeight));
    }

    private MatchDetailVO toDetailVO(MatchDetail detail) {
        MatchDetailVO vo = new MatchDetailVO();
        vo.setSkill(detail.getSkill());
        vo.setStatus(detail.getMatchStatus());
        vo.setEvidence(detail.getEvidence());
        vo.setSuggestion(detail.getSuggestion());
        return vo;
    }

    private static String normalizePosition(String position) {
        if (position == null) {
            return "";
        }
        return position.replaceAll("[\\s\\-_/]", "").toLowerCase(Locale.ROOT);
    }

    /**
     * 同类岗位历史反馈统计（docs/api.md §9.1 规则 2）：
     * 以岗位名称精确匹配为「同类」，统计已读率与面试率，给予小幅加权。
     * 样本为空或没有正面反馈时不调整，保证排序可解释。
     */
    private static final class HistoryStats {

        private static final BigDecimal READ_WEIGHT = new BigDecimal("0.10");
        private static final BigDecimal INTERVIEW_WEIGHT = new BigDecimal("0.15");
        private static final BigDecimal MAX_FACTOR = new BigDecimal("1.25");

        private int sample;
        private int read;
        private int interview;

        static Map<String, HistoryStats> group(List<JobApplication> applications,
                                               Map<Long, Job> jobById) {
            Map<String, HistoryStats> result = new HashMap<>();
            for (JobApplication application : applications) {
                ApplicationStatus status;
                try {
                    status = ApplicationStatus.valueOf(application.getStatus());
                } catch (RuntimeException e) {
                    continue;
                }
                // 未投递/已撤回/投递失败不参与反馈统计
                if (status == ApplicationStatus.PENDING
                        || status == ApplicationStatus.WITHDRAWN
                        || status == ApplicationStatus.FAILED) {
                    continue;
                }
                Job job = jobById.get(application.getJobId());
                if (job == null || job.getPosition() == null) {
                    continue;
                }
                HistoryStats stats = result.computeIfAbsent(
                        normalizePosition(job.getPosition()), key -> new HistoryStats());
                stats.sample++;
                if (ApplicationStatus.READ_STATUSES.contains(status)) {
                    stats.read++;
                }
                if (ApplicationStatus.INTERVIEW_STATUSES.contains(status)) {
                    stats.interview++;
                }
            }
            return result;
        }

        BigDecimal factor() {
            if (sample == 0) {
                return BigDecimal.ONE;
            }
            BigDecimal readRate = BigDecimal.valueOf(read)
                    .divide(BigDecimal.valueOf(sample), 4, RoundingMode.HALF_UP);
            BigDecimal interviewRate = BigDecimal.valueOf(interview)
                    .divide(BigDecimal.valueOf(sample), 4, RoundingMode.HALF_UP);
            BigDecimal factor = BigDecimal.ONE
                    .add(readRate.multiply(READ_WEIGHT))
                    .add(interviewRate.multiply(INTERVIEW_WEIGHT));
            return factor.min(MAX_FACTOR).setScale(4, RoundingMode.HALF_UP);
        }

        String reason() {
            long readPercent = Math.round(read * 100.0 / sample);
            long interviewPercent = Math.round(interview * 100.0 / sample);
            return "同类岗位历史已读率 " + readPercent + "%、面试率 " + interviewPercent
                    + "%（样本 " + sample + "），排序小幅上调";
        }
    }

    /**
     * 反馈后置规则（docs/database.md §3.7）：
     * NOT_INTERESTED → 同岗位/同公司 ×0.5；LOCATION_MISMATCH → 该城市 ×0.8；
     * SALARY_MISMATCH → 同岗位 ×0.8；SKILL_ALREADY_HAVE 不影响排序。
     */
    private static class FeedbackRules {

        private final Set<String> notInterestedPositions = new HashSet<>();
        private final Set<String> notInterestedCompanies = new HashSet<>();
        private final Set<String> locationMismatchCities = new HashSet<>();
        private final Set<String> salaryMismatchPositions = new HashSet<>();

        FeedbackRules(List<UserFeedback> feedbacks, List<Job> jobs) {
            Map<Long, Job> jobById = jobs.stream().collect(Collectors.toMap(Job::getId, j -> j));
            for (UserFeedback feedback : feedbacks) {
                Job source = jobById.get(feedback.getJobId());
                if (source == null) {
                    continue;
                }
                switch (feedback.getFeedbackType()) {
                    case "NOT_INTERESTED" -> {
                        if (source.getPosition() != null) {
                            notInterestedPositions.add(source.getPosition());
                        }
                        if (source.getCompany() != null) {
                            notInterestedCompanies.add(source.getCompany());
                        }
                    }
                    case "LOCATION_MISMATCH" -> {
                        if (source.getCity() != null) {
                            locationMismatchCities.add(source.getCity());
                        }
                    }
                    case "SALARY_MISMATCH" -> {
                        if (source.getPosition() != null) {
                            salaryMismatchPositions.add(source.getPosition());
                        }
                    }
                    default -> {
                        // SKILL_ALREADY_HAVE 不影响排序
                    }
                }
            }
        }

        BigDecimal factorFor(RecommendItemVO item) {
            BigDecimal factor = BigDecimal.ONE;
            if (notInterestedPositions.contains(item.getPosition())
                    || notInterestedCompanies.contains(item.getCompany())) {
                factor = factor.multiply(new BigDecimal("0.5"));
            }
            if (locationMismatchCities.contains(item.getCity())) {
                factor = factor.multiply(new BigDecimal("0.8"));
            }
            if (salaryMismatchPositions.contains(item.getPosition())) {
                factor = factor.multiply(new BigDecimal("0.8"));
            }
            return factor;
        }

        List<String> reasonsFor(RecommendItemVO item) {
            List<String> reasons = new ArrayList<>();
            if (notInterestedPositions.contains(item.getPosition())
                    || notInterestedCompanies.contains(item.getCompany())) {
                reasons.add("你曾对同类岗位（或同公司岗位）标记不感兴趣，已后置");
            }
            if (locationMismatchCities.contains(item.getCity())) {
                reasons.add("你反馈过该城市地点不合适，已后置");
            }
            if (salaryMismatchPositions.contains(item.getPosition())) {
                reasons.add("你反馈过该岗位薪资不合适，已后置");
            }
            return reasons;
        }
    }
}
