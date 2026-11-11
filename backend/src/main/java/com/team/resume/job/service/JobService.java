package com.team.resume.job.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.JdParseResult;
import com.team.resume.common.BusinessException;
import com.team.resume.common.PageResult;
import com.team.resume.job.dto.CreateJobRequest;
import com.team.resume.job.dto.ParseJdRequest;
import com.team.resume.job.entity.Job;
import com.team.resume.job.entity.JobRequirement;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.mapper.JobRequirementMapper;
import com.team.resume.job.vo.JobDetailVO;
import com.team.resume.job.vo.JobParseVO;
import com.team.resume.job.vo.JobRequirementVO;
import com.team.resume.job.vo.JobSummaryVO;

/**
 * 岗位业务（docs/api.md §8）：
 * - 列表分页筛选（keyword 匹配岗位/公司、city 精确、salary 模糊）
 * - 详情含技能要求
 * - 手动新增岗位（sourceType=MANUAL）
 * - AI 解析 JD：解析结果覆盖该岗位的 job_requirement（重复解析以最新为准）
 */
@Service
public class JobService {

    private static final long MAX_PAGE_SIZE = 50;

    private final JobMapper jobMapper;
    private final JobRequirementMapper requirementMapper;
    private final AIService aiService;

    public JobService(JobMapper jobMapper, JobRequirementMapper requirementMapper,
                      AIService aiService) {
        this.jobMapper = jobMapper;
        this.requirementMapper = requirementMapper;
        this.aiService = aiService;
    }

    public PageResult<JobSummaryVO> list(long page, long size, String keyword,
                                         String city, String salary) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Job::getPosition, kw).or().like(Job::getCompany, kw));
        }
        if (city != null && !city.isBlank()) {
            wrapper.eq(Job::getCity, city.trim());
        }
        if (salary != null && !salary.isBlank()) {
            wrapper.like(Job::getSalary, salary.trim());
        }
        wrapper.orderByDesc(Job::getId);

        Page<Job> result = jobMapper.selectPage(Page.of(safePage, safeSize), wrapper);

        List<Long> jobIds = result.getRecords().stream().map(Job::getId).toList();
        Map<Long, List<JobRequirement>> requirementMap = jobIds.isEmpty()
                ? Map.of()
                : requirementMapper.selectList(new LambdaQueryWrapper<JobRequirement>()
                        .in(JobRequirement::getJobId, jobIds))
                        .stream()
                        .collect(Collectors.groupingBy(JobRequirement::getJobId));

        List<JobSummaryVO> items = result.getRecords().stream()
                .map(job -> toSummary(job, requirementMap.getOrDefault(job.getId(), List.of())))
                .toList();
        return new PageResult<>(items, result.getTotal(), safePage, safeSize);
    }

    public JobDetailVO detail(Long jobId) {
        Job job = requireJob(jobId);
        List<JobRequirement> requirements = listRequirements(jobId);
        return toDetail(job, requirements);
    }

    public JobSummaryVO create(CreateJobRequest request) {
        Job job = new Job();
        job.setCompany(trimToNull(request.getCompany()));
        job.setPosition(request.getPosition().trim());
        job.setCity(trimToNull(request.getCity()));
        job.setSalary(trimToNull(request.getSalary()));
        job.setJdContent(trimToNull(request.getJdContent()));
        job.setExternalUrl(trimToNull(request.getExternalUrl()));
        job.setSourceType("MANUAL");
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        jobMapper.insert(job);
        return toSummary(job, List.of());
    }

    @Transactional
    public JobParseVO parse(Long jobId, ParseJdRequest request) {
        Job job = requireJob(jobId);

        String jd = request.getJdContent() == null || request.getJdContent().isBlank()
                ? job.getJdContent()
                : request.getJdContent();
        if (jd == null || jd.isBlank()) {
            throw new BusinessException(400, "缺少 JD 内容：请先在岗位中填写 JD 或随请求提供");
        }

        JdParseResult parsed = aiService.parseJd(jd);

        // 解析结果覆盖旧技能要求（重复解析以最新为准；人工修正能力后续版本提供）
        requirementMapper.delete(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getJobId, jobId));
        for (JdParseResult.Requirement req : parsed.getRequirements()) {
            JobRequirement entity = new JobRequirement();
            entity.setJobId(jobId);
            entity.setSkill(req.getSkill());
            entity.setImportance(req.getImportance());
            entity.setWeight(req.getWeight());
            requirementMapper.insert(entity);
        }

        // 岗位名称为空时用解析结果补全；JD 内容以最新提供的为准
        boolean needUpdate = false;
        if ((job.getPosition() == null || job.getPosition().isBlank())
                && parsed.getPosition() != null && !parsed.getPosition().isBlank()) {
            job.setPosition(parsed.getPosition().trim());
            needUpdate = true;
        }
        if (request.getJdContent() != null && !request.getJdContent().isBlank()
                && !request.getJdContent().equals(job.getJdContent())) {
            job.setJdContent(request.getJdContent());
            needUpdate = true;
        }
        if (needUpdate) {
            job.setUpdatedAt(LocalDateTime.now());
            jobMapper.updateById(job);
        }

        JobParseVO vo = new JobParseVO();
        vo.setJobId(jobId);
        vo.setPosition(parsed.getPosition());
        vo.setRequirements(listRequirements(jobId).stream()
                .map(JobRequirementVO::from)
                .toList());
        return vo;
    }

    private JobSummaryVO toSummary(Job job, List<JobRequirement> requirements) {
        JobSummaryVO vo = new JobSummaryVO();
        vo.setId(job.getId());
        vo.setCompany(job.getCompany());
        vo.setPosition(job.getPosition());
        vo.setCity(job.getCity());
        vo.setSalary(job.getSalary());
        vo.setSourceType(job.getSourceType());
        vo.setRequirementCount(requirements.size());
        vo.setTopSkills(requirements.stream()
                .filter(r -> "REQUIRED".equals(r.getImportance()))
                .map(JobRequirement::getSkill)
                .limit(4)
                .toList());
        return vo;
    }

    private JobDetailVO toDetail(Job job, List<JobRequirement> requirements) {
        JobDetailVO vo = new JobDetailVO();
        vo.setId(job.getId());
        vo.setCompany(job.getCompany());
        vo.setPosition(job.getPosition());
        vo.setCity(job.getCity());
        vo.setSalary(job.getSalary());
        vo.setJdContent(job.getJdContent());
        vo.setSourceType(job.getSourceType());
        vo.setExternalUrl(job.getExternalUrl());
        vo.setRequirements(requirements.stream().map(JobRequirementVO::from).toList());
        return vo;
    }

    private List<JobRequirement> listRequirements(Long jobId) {
        return requirementMapper.selectList(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getJobId, jobId)
                .orderByDesc(JobRequirement::getWeight));
    }

    private Job requireJob(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return job;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
