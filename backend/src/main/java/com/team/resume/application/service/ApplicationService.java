package com.team.resume.application.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.resume.application.ApplicationStatus;
import com.team.resume.application.dto.CreateApplicationRequest;
import com.team.resume.application.dto.FollowUpRequest;
import com.team.resume.application.dto.UpdateApplicationStatusRequest;
import com.team.resume.application.entity.ApplicationHistory;
import com.team.resume.application.entity.JobApplication;
import com.team.resume.application.mapper.ApplicationHistoryMapper;
import com.team.resume.application.mapper.JobApplicationMapper;
import com.team.resume.application.vo.ApplicationHistoryVO;
import com.team.resume.application.vo.ApplicationVO;
import com.team.resume.application.vo.FollowUpItemVO;
import com.team.resume.application.vo.FollowUpListVO;
import com.team.resume.common.BusinessException;
import com.team.resume.common.PageResult;
import com.team.resume.job.entity.Job;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;

/**
 * 投递业务（docs/api.md §11）：
 * - 模拟一键投递：创建即 PENDING → APPLIED，并写首条状态历史
 * - 状态更新：单事务内校验状态机、写历史、重置提醒计时
 * - 主动提醒：按 job_application 的时间字段计算，不引入定时框架
 * 所有查询严格按 user_id 隔离（database.md §8）。
 */
@Service
public class ApplicationService {

    private static final int FOLLOW_UP_DAYS = 3;
    private static final String ACTION_COMPLETED = "COMPLETED";

    private final JobApplicationMapper applicationMapper;
    private final ApplicationHistoryMapper historyMapper;
    private final JobMapper jobMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;

    public ApplicationService(JobApplicationMapper applicationMapper,
                              ApplicationHistoryMapper historyMapper,
                              JobMapper jobMapper,
                              ResumeMapper resumeMapper,
                              ResumeVersionMapper resumeVersionMapper) {
        this.applicationMapper = applicationMapper;
        this.historyMapper = historyMapper;
        this.jobMapper = jobMapper;
        this.resumeMapper = resumeMapper;
        this.resumeVersionMapper = resumeVersionMapper;
    }

    @Transactional
    public ApplicationVO create(Long userId, CreateApplicationRequest request) {
        Job job = requireJob(request.getJobId());
        ResumeVersion version = requireOwnedVersion(userId, request.getResumeVersionId());

        boolean duplicated = applicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .eq(JobApplication::getJobId, job.getId())) > 0;
        if (duplicated) {
            throw new BusinessException(409, "该岗位已投递，请勿重复投递");
        }

        LocalDateTime now = LocalDateTime.now();
        JobApplication application = new JobApplication();
        application.setUserId(userId);
        application.setJobId(job.getId());
        application.setResumeVersionId(version.getId());
        application.setStatus(ApplicationStatus.APPLIED.name());
        application.setAppliedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);

        insertHistory(application.getId(), ApplicationStatus.PENDING.name(),
                ApplicationStatus.APPLIED.name(), "模拟一键投递", now);

        return toVO(application, job, version);
    }

    public PageResult<ApplicationVO> list(Long userId, long page, long size, String status) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 50);

        LambdaQueryWrapper<JobApplication> wrapper = new LambdaQueryWrapper<JobApplication>()
                .eq(JobApplication::getUserId, userId)
                .orderByDesc(JobApplication::getUpdatedAt)
                .orderByDesc(JobApplication::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(JobApplication::getStatus, ApplicationStatus.from(status).name());
        }

        Page<JobApplication> result = applicationMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        List<ApplicationVO> items = toVOs(result.getRecords());
        return new PageResult<>(items, result.getTotal(), safePage, safeSize);
    }

    public ApplicationVO detail(Long userId, Long applicationId) {
        JobApplication application = requireOwnedApplication(userId, applicationId);
        return toVOs(List.of(application)).get(0);
    }

    public List<ApplicationHistoryVO> history(Long userId, Long applicationId) {
        requireOwnedApplication(userId, applicationId);
        return historyMapper.selectList(new LambdaQueryWrapper<ApplicationHistory>()
                        .eq(ApplicationHistory::getApplicationId, applicationId)
                        .orderByAsc(ApplicationHistory::getCreatedAt)
                        .orderByAsc(ApplicationHistory::getId))
                .stream()
                .map(this::toHistoryVO)
                .toList();
    }

    @Transactional
    public ApplicationVO updateStatus(Long userId, Long applicationId,
                                      UpdateApplicationStatusRequest request) {
        JobApplication application = requireOwnedApplication(userId, applicationId);
        ApplicationStatus current = ApplicationStatus.from(application.getStatus());
        ApplicationStatus target = ApplicationStatus.from(request.getStatus());
        if (current == target) {
            throw new BusinessException(409, "投递状态未发生变化");
        }
        if (!current.canTransitionTo(target)) {
            throw new BusinessException(409,
                    "不允许从 " + current.name() + " 流转到 " + target.name()
                            + "，可选状态：" + String.join(" / ", current.allowedTargets()));
        }

        LocalDateTime now = LocalDateTime.now();
        application.setStatus(target.name());
        application.setUpdatedAt(now);
        // 状态更新自动重置提醒计时（database.md §4 ⑤）
        application.setLastFollowUpAt(now);
        application.setNextFollowUpAt(now.plusDays(FOLLOW_UP_DAYS));
        applicationMapper.updateById(application);

        insertHistory(application.getId(), current.name(), target.name(),
                trimToNull(request.getNote()), now);

        Job job = jobMapper.selectById(application.getJobId());
        ResumeVersion version = resumeVersionMapper.selectById(application.getResumeVersionId());
        return toVO(application, job, version);
    }

    public FollowUpListVO followUps(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<String> statuses = ApplicationStatus.FOLLOW_UP_STATUSES.stream()
                .map(Enum::name)
                .toList();
        List<JobApplication> candidates = applicationMapper.selectList(
                new LambdaQueryWrapper<JobApplication>()
                        .eq(JobApplication::getUserId, userId)
                        .in(JobApplication::getStatus, statuses));
        if (candidates.isEmpty()) {
            return new FollowUpListVO(0, List.of());
        }

        Map<Long, Job> jobs = loadJobs(candidates);
        List<FollowUpItemVO> items = new ArrayList<>();
        for (JobApplication application : candidates) {
            LocalDateTime lastUpdate = lastUpdateAt(application);
            LocalDateTime due = application.getNextFollowUpAt() != null
                    ? application.getNextFollowUpAt()
                    : lastUpdate.plusDays(FOLLOW_UP_DAYS);
            if (due.isAfter(now)) {
                continue;
            }
            Job job = jobs.get(application.getJobId());
            FollowUpItemVO item = new FollowUpItemVO();
            item.setApplicationId(application.getId());
            item.setJobId(application.getJobId());
            item.setCompany(job != null ? job.getCompany() : null);
            item.setPosition(job != null ? job.getPosition() : null);
            item.setStatus(application.getStatus());
            item.setLastUpdateAt(lastUpdate);
            long days = ChronoUnit.DAYS.between(lastUpdate, now);
            item.setDaysNoResponse(Math.max(days, 0));
            items.add(item);
        }
        items.sort(Comparator.comparing(FollowUpItemVO::getLastUpdateAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return new FollowUpListVO(items.size(), items);
    }

    @Transactional
    public ApplicationVO handleFollowUp(Long userId, Long applicationId, FollowUpRequest request) {
        JobApplication application = requireOwnedApplication(userId, applicationId);
        if (!ACTION_COMPLETED.equalsIgnoreCase(request.getAction())) {
            throw new BusinessException(400, "action 目前只支持 COMPLETED");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = request.getNextFollowUpAt() == null
                || request.getNextFollowUpAt().isBlank()
                ? now.plusDays(FOLLOW_UP_DAYS)
                : parseDateTime(request.getNextFollowUpAt());
        if (!next.isAfter(now)) {
            throw new BusinessException(400, "下次提醒时间必须晚于当前时间");
        }

        application.setLastFollowUpAt(now);
        application.setNextFollowUpAt(next);
        application.setUpdatedAt(now);
        applicationMapper.updateById(application);

        Job job = jobMapper.selectById(application.getJobId());
        ResumeVersion version = resumeVersionMapper.selectById(application.getResumeVersionId());
        return toVO(application, job, version);
    }

    // ---------- 内部工具 ----------

    private JobApplication requireOwnedApplication(Long userId, Long applicationId) {
        JobApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new BusinessException(404, "投递记录不存在");
        }
        if (!Objects.equals(application.getUserId(), userId)) {
            throw new BusinessException(403, "无权访问该投递记录");
        }
        return application;
    }

    private Job requireJob(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return job;
    }

    private ResumeVersion requireOwnedVersion(Long userId, Long versionId) {
        ResumeVersion version = resumeVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        Resume resume = resumeMapper.selectById(version.getResumeId());
        if (resume == null || !Objects.equals(resume.getUserId(), userId)) {
            throw new BusinessException(403, "该简历版本不属于当前用户");
        }
        return version;
    }

    private void insertHistory(Long applicationId, String oldStatus, String newStatus,
                               String note, LocalDateTime createdAt) {
        ApplicationHistory history = new ApplicationHistory();
        history.setApplicationId(applicationId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setNote(note);
        history.setCreatedAt(createdAt);
        historyMapper.insert(history);
    }

    private List<ApplicationVO> toVOs(List<JobApplication> applications) {
        if (applications.isEmpty()) {
            return List.of();
        }
        Map<Long, Job> jobs = loadJobs(applications);
        Map<Long, ResumeVersion> versions = loadVersions(applications);
        return applications.stream()
                .map(a -> toVO(a, jobs.get(a.getJobId()), versions.get(a.getResumeVersionId())))
                .toList();
    }

    private Map<Long, Job> loadJobs(List<JobApplication> applications) {
        List<Long> ids = applications.stream()
                .map(JobApplication::getJobId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jobMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
    }

    private Map<Long, ResumeVersion> loadVersions(List<JobApplication> applications) {
        List<Long> ids = applications.stream()
                .map(JobApplication::getResumeVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return resumeVersionMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ResumeVersion::getId, Function.identity()));
    }

    private ApplicationVO toVO(JobApplication application, Job job, ResumeVersion version) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(application.getId());
        vo.setJobId(application.getJobId());
        vo.setCompany(job != null ? job.getCompany() : null);
        vo.setPosition(job != null ? job.getPosition() : null);
        vo.setCity(job != null ? job.getCity() : null);
        vo.setSalary(job != null ? job.getSalary() : null);
        vo.setResumeVersionId(application.getResumeVersionId());
        vo.setVersionNumber(version != null ? version.getVersionNumber() : null);
        vo.setStatus(application.getStatus());
        vo.setAppliedAt(application.getAppliedAt());
        vo.setNextFollowUpAt(application.getNextFollowUpAt());
        vo.setLastFollowUpAt(application.getLastFollowUpAt());
        vo.setUpdatedAt(application.getUpdatedAt());
        return vo;
    }

    private ApplicationHistoryVO toHistoryVO(ApplicationHistory history) {
        ApplicationHistoryVO vo = new ApplicationHistoryVO();
        vo.setId(history.getId());
        vo.setOldStatus(history.getOldStatus());
        vo.setNewStatus(history.getNewStatus());
        vo.setNote(history.getNote());
        vo.setCreatedAt(history.getCreatedAt());
        return vo;
    }

    private static LocalDateTime lastUpdateAt(JobApplication application) {
        if (application.getUpdatedAt() != null) {
            return application.getUpdatedAt();
        }
        if (application.getAppliedAt() != null) {
            return application.getAppliedAt();
        }
        return LocalDateTime.now();
    }

    private static String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 兼容 2026-11-15T10:00:00+08:00、2026-11-15T10:00:00 与 UTC Instant 三种写法。 */
    private static LocalDateTime parseDateTime(String raw) {
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 尝试本地时间
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // 尝试 Instant
        }
        try {
            return Instant.parse(value)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "nextFollowUpAt 时间格式不合法");
        }
    }
}
