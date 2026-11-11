package com.team.resume.job.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.JobRequirementsResult;
import com.team.resume.common.BusinessException;
import com.team.resume.common.PageVO;
import com.team.resume.job.dto.JdParseRequest;
import com.team.resume.job.dto.JobCreateRequest;
import com.team.resume.job.dto.JobRequirementRequest;
import com.team.resume.job.entity.Job;
import com.team.resume.job.entity.JobRequirement;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.mapper.JobRequirementMapper;
import com.team.resume.job.vo.JdParseResultVO;
import com.team.resume.job.vo.JobDetailVO;
import com.team.resume.job.vo.JobRequirementVO;
import com.team.resume.job.vo.JobSummaryVO;

/**
 * 岗位中心（docs/api.md §8）：
 * - 列表支持 keyword/city/salary 筛选和分页；
 * - JD 解析由 AI 提取，Java 负责结构校验与权重归一化；
 * - 岗位是全局数据，不做 user_id 过滤，但接口仍要求登录。
 */
@Service
public class JobService {

    private static final int MAX_PAGE_SIZE = 50;

    private final JobMapper jobMapper;
    private final JobRequirementMapper requirementMapper;
    private final AIService aiService;

    public JobService(JobMapper jobMapper,
                      JobRequirementMapper requirementMapper,
                      AIService aiService) {
        this.jobMapper = jobMapper;
        this.requirementMapper = requirementMapper;
        this.aiService = aiService;
    }

    public PageVO<JobSummaryVO> list(int page, int size, String keyword, String city, String salary) {
        if (page < 1 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "page/size 参数不合法");
        }

        LambdaQueryWrapper<Job> wrapper = buildQuery(keyword, city, salary);
        Long totalValue = jobMapper.selectCount(wrapper);
        long total = totalValue == null ? 0 : totalValue;

        List<JobSummaryVO> records = List.of();
        if (total > 0) {
            wrapper.orderByDesc(Job::getCreatedAt).orderByDesc(Job::getId);
            long offset = (long) (page - 1) * size;
            records = jobMapper.selectList(wrapper.last("LIMIT " + size + " OFFSET " + offset))
                    .stream()
                    .map(JobService::toSummary)
                    .toList();
        }
        return new PageVO<>(total, page, size, records);
    }

    public JobDetailVO detail(Long jobId) {
        Job job = requireJob(jobId);
        return toDetail(job, listRequirements(jobId));
    }

    @Transactional
    public JobDetailVO create(JobCreateRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        String company = requireText(request.getCompany(), "公司不能为空", 200, "公司");
        String position = requireText(request.getPosition(), "岗位名称不能为空", 200, "岗位名称");
        String city = optionalText(request.getCity(), 100, "城市");
        String salary = optionalText(request.getSalary(), 100, "薪资");
        String externalUrl = optionalText(request.getExternalUrl(), 500, "外部链接");

        LocalDateTime now = LocalDateTime.now();
        Job job = new Job();
        job.setCompany(company);
        job.setPosition(position);
        job.setCity(city);
        job.setSalary(salary);
        job.setJdContent(trimToNull(request.getJdContent()));
        job.setSourceType(Job.SOURCE_MANUAL);
        job.setExternalUrl(externalUrl);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobMapper.insert(job);

        List<JobRequirement> requirements = createRequirements(job.getId(), request.getRequirements());
        return toDetail(job, requirements);
    }

    /**
     * 解析岗位 JD：优先使用请求体中的 jdContent（便于人工修正后重新解析），
     * 否则使用岗位已保存的 JD。解析成功后覆盖该岗位的 job_requirement。
     */
    @Transactional
    public JdParseResultVO parse(Long jobId, JdParseRequest request) {
        Job job = requireJob(jobId);
        String requestedJd = request == null ? null : trimToNull(request.getJdContent());
        String jd = requestedJd != null ? requestedJd : trimToNull(job.getJdContent());
        if (jd == null) {
            throw new BusinessException(400, "岗位 JD 为空，无法解析");
        }

        JobRequirementsResult aiResult = aiService.parseJobDescription(jd);
        List<JobRequirementsResult.Requirement> normalized = JobRequirementRules.normalize(
                aiResult == null ? null : aiResult.requirements());
        if (normalized.isEmpty()) {
            throw new BusinessException(502, "AI 未解析出有效的技能要求，请稍后重试");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean jobChanged = false;
        if (requestedJd != null) {
            job.setJdContent(requestedJd);
            jobChanged = true;
        }
        String parsedPosition = aiResult.position() == null ? null : aiResult.position().trim();
        if (parsedPosition != null && !parsedPosition.isEmpty()
                && trimToNull(job.getPosition()) == null) {
            job.setPosition(parsedPosition);
            jobChanged = true;
        }
        if (jobChanged) {
            job.setUpdatedAt(now);
            jobMapper.updateById(job);
        }

        requirementMapper.delete(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getJobId, jobId));

        List<JobRequirement> saved = new ArrayList<>();
        for (JobRequirementsResult.Requirement item : normalized) {
            JobRequirement entity = new JobRequirement();
            entity.setJobId(jobId);
            entity.setSkill(item.skill());
            entity.setImportance(item.importance());
            entity.setWeight(item.weight());
            requirementMapper.insert(entity);
            saved.add(entity);
        }

        return new JdParseResultVO(
                parsedPosition == null || parsedPosition.isEmpty() ? job.getPosition() : parsedPosition,
                saved.stream().map(JobService::toRequirementVO).toList());
    }

    private List<JobRequirement> createRequirements(Long jobId, List<JobRequirementRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        List<JobRequirementsResult.Requirement> raw = requests.stream()
                .map(item -> new JobRequirementsResult.Requirement(
                        item.getSkill(), item.getImportance(), item.getWeight()))
                .toList();
        List<JobRequirementsResult.Requirement> normalized = JobRequirementRules.normalize(raw);
        if (normalized.isEmpty()) {
            throw new BusinessException(400, "技能要求格式错误");
        }

        List<JobRequirement> saved = new ArrayList<>();
        for (JobRequirementsResult.Requirement item : normalized) {
            JobRequirement entity = new JobRequirement();
            entity.setJobId(jobId);
            entity.setSkill(item.skill());
            entity.setImportance(item.importance());
            entity.setWeight(item.weight());
            requirementMapper.insert(entity);
            saved.add(entity);
        }
        return saved;
    }

    private static LambdaQueryWrapper<Job> buildQuery(String keyword, String city, String salary) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(Job::getPosition, normalizedKeyword)
                    .or()
                    .like(Job::getCompany, normalizedKeyword));
        }
        String normalizedCity = trimToNull(city);
        if (normalizedCity != null) {
            wrapper.eq(Job::getCity, normalizedCity);
        }
        String normalizedSalary = trimToNull(salary);
        if (normalizedSalary != null) {
            wrapper.like(Job::getSalary, normalizedSalary);
        }
        return wrapper;
    }

    private Job requireJob(Long jobId) {
        Job job = jobId == null ? null : jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return job;
    }

    private List<JobRequirement> listRequirements(Long jobId) {
        return requirementMapper.selectList(new LambdaQueryWrapper<JobRequirement>()
                .eq(JobRequirement::getJobId, jobId)
                .orderByAsc(JobRequirement::getId));
    }

    private static JobSummaryVO toSummary(Job job) {
        return new JobSummaryVO(job.getId(), job.getCompany(), job.getPosition(), job.getCity(),
                job.getSalary(), job.getSourceType(), job.getExternalUrl(), job.getCreatedAt());
    }

    private static JobDetailVO toDetail(Job job, List<JobRequirement> requirements) {
        return new JobDetailVO(job.getId(), job.getCompany(), job.getPosition(), job.getCity(),
                job.getSalary(), job.getJdContent(), job.getSourceType(), job.getExternalUrl(),
                job.getCreatedAt(), job.getUpdatedAt(),
                requirements.stream().map(JobService::toRequirementVO).toList());
    }

    private static JobRequirementVO toRequirementVO(JobRequirement requirement) {
        return new JobRequirementVO(requirement.getId(), requirement.getSkill(),
                requirement.getImportance(), requirement.getWeight());
    }

    private static String requireText(String value, String emptyMessage, int maxLength, String fieldName) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BusinessException(400, emptyMessage);
        }
        if (text.length() > maxLength) {
            throw new BusinessException(400, fieldName + "不能超过" + maxLength + "个字符");
        }
        return text;
    }

    private static String optionalText(String value, int maxLength, String fieldName) {
        String text = trimToNull(value);
        if (text != null && text.length() > maxLength) {
            throw new BusinessException(400, fieldName + "不能超过" + maxLength + "个字符");
        }
        return text;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
