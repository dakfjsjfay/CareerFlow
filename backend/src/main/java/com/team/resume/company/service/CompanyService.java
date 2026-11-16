package com.team.resume.company.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.resume.common.BusinessException;
import com.team.resume.common.PageResult;
import com.team.resume.company.entity.Company;
import com.team.resume.company.mapper.CompanyMapper;
import com.team.resume.company.vo.CompanyDetailVO;
import com.team.resume.company.vo.CompanySummaryVO;
import com.team.resume.company.vo.PlatformLinkVO;
import com.team.resume.job.entity.Job;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JobSummaryVO;

/**
 * 企业信息库业务：
 * - 企业列表分页筛选（keyword 匹配全称/简称、industry、city、verificationStatus）
 * - 详情含当前用户可见的关联岗位数
 * - 关联岗位复用 JobService 的可见性规则
 * - 第三方平台入口仅返回外部搜索页 URL，不做自动投递
 * - 链接核验仅管理员可写
 */
@Service
public class CompanyService {

    private static final long MAX_PAGE_SIZE = 50;

    private static final String PLATFORM_NOTE = "跳转到该平台的搜索页，需自行在站内投递，CareerFlow 不代投";

    private record PlatformTemplate(String key, String label, String urlTemplate) {
    }

    private static final List<PlatformTemplate> PLATFORM_TEMPLATES = List.of(
            new PlatformTemplate("BOSS直聘", "BOSS直聘", "https://www.zhipin.com/web/geek/job?query=%s"),
            new PlatformTemplate("智联招聘", "智联招聘", "https://sou.zhaopin.com/?kw=%s"),
            new PlatformTemplate("前程无忧", "前程无忧", "https://we.51job.com/pc/search?keyword=%s"),
            new PlatformTemplate("拉勾", "拉勾", "https://www.lagou.com/wn/jobs?kd=%s"),
            new PlatformTemplate("实习僧", "实习僧", "https://www.shixiseng.com/interns?keyword=%s"),
            new PlatformTemplate("牛客", "牛客", "https://www.nowcoder.com/jobs/recommend/campus?searchWord=%s"));

    private static final Set<String> VERIFICATION_STATUSES = Set.of(
            Company.STATUS_VERIFIED, Company.STATUS_PENDING, Company.STATUS_EXPIRED);

    private final CompanyMapper companyMapper;
    private final JobMapper jobMapper;
    private final JobService jobService;

    public CompanyService(CompanyMapper companyMapper, JobMapper jobMapper, JobService jobService) {
        this.companyMapper = companyMapper;
        this.jobMapper = jobMapper;
        this.jobService = jobService;
    }

    public PageResult<CompanySummaryVO> list(long page, long size, String keyword,
                                             String industry, String city,
                                             String verificationStatus) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Company::getStatus, Company.ACTIVE);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Company::getName, kw).or().like(Company::getShortName, kw));
        }
        if (industry != null && !industry.isBlank()) {
            wrapper.eq(Company::getIndustry, industry.trim());
        }
        if (city != null && !city.isBlank()) {
            wrapper.eq(Company::getCity, city.trim());
        }
        if (verificationStatus != null && !verificationStatus.isBlank()) {
            wrapper.eq(Company::getVerificationStatus, verificationStatus.trim().toUpperCase());
        }
        wrapper.orderByAsc(Company::getId);

        Page<Company> result = companyMapper.selectPage(Page.of(safePage, safeSize), wrapper);
        List<CompanySummaryVO> items = result.getRecords().stream().map(this::toSummary).toList();
        return new PageResult<>(items, result.getTotal(), safePage, safeSize);
    }

    public CompanyDetailVO detail(Long companyId, long userId, boolean admin) {
        Company company = requireCompany(companyId);
        CompanyDetailVO vo = new CompanyDetailVO();
        BeanUtils.copyProperties(company, vo);
        vo.setJobCount(countVisibleJobs(company.getId(), userId, admin));
        return vo;
    }

    public PageResult<JobSummaryVO> jobs(Long companyId, long page, long size,
                                         long userId, boolean admin) {
        requireCompany(companyId);
        return jobService.list(page, size, null, null, null,
                companyId, null, null, userId, admin);
    }

    public List<PlatformLinkVO> platformLinks(Long companyId) {
        Company company = requireCompany(companyId);
        String keyword = company.getShortName() == null || company.getShortName().isBlank()
                ? company.getName()
                : company.getShortName();
        String encoded = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

        List<PlatformLinkVO> links = new ArrayList<>();
        for (PlatformTemplate template : PLATFORM_TEMPLATES) {
            links.add(new PlatformLinkVO(template.key(), template.label(),
                    String.format(template.urlTemplate(), encoded), PLATFORM_NOTE));
        }
        return links;
    }

    @Transactional
    public CompanyDetailVO verify(Long companyId, String status, long userId, boolean admin) {
        if (!admin) {
            throw new BusinessException(403, "仅管理员可核实企业信息");
        }
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!VERIFICATION_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "核验状态仅支持 VERIFIED / PENDING / EXPIRED");
        }
        Company company = requireCompany(companyId);
        company.setVerificationStatus(normalized);
        company.setLastVerifiedAt(LocalDateTime.now());
        companyMapper.updateById(company);
        return detail(companyId, userId, admin);
    }

    private CompanySummaryVO toSummary(Company company) {
        CompanySummaryVO vo = new CompanySummaryVO();
        BeanUtils.copyProperties(company, vo);
        return vo;
    }

    private long countVisibleJobs(Long companyId, long userId, boolean admin) {
        LambdaQueryWrapper<Job> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Job::getCompanyId, companyId);
        if (!admin) {
            wrapper.and(w -> w.eq(Job::getVisibility, Job.VISIBILITY_PUBLIC)
                    .or().eq(Job::getCreatedBy, userId));
        }
        return jobMapper.selectCount(wrapper);
    }

    private Company requireCompany(Long companyId) {
        Company company = companyId == null ? null : companyMapper.selectById(companyId);
        if (company == null) {
            throw new BusinessException(404, "企业不存在");
        }
        return company;
    }
}
