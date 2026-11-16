package com.team.resume.company.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.common.PageResult;
import com.team.resume.common.Result;
import com.team.resume.company.service.CompanyService;
import com.team.resume.company.vo.CompanyDetailVO;
import com.team.resume.company.vo.CompanySummaryVO;
import com.team.resume.company.vo.PlatformLinkVO;
import com.team.resume.job.vo.JobSummaryVO;

/**
 * 企业信息库接口：
 * GET  /api/companies                     列表（分页 + keyword/industry/city/verificationStatus）
 * GET  /api/companies/{id}                详情（含当前用户可见岗位数）
 * GET  /api/companies/{id}/jobs           企业关联岗位（复用岗位可见性规则）
 * GET  /api/companies/{id}/platform-links 第三方招聘平台搜索入口（仅跳转，不自动投递）
 * POST /api/companies/{id}/verify         管理员标记链接核验状态
 */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public Result<PageResult<CompanySummaryVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String verificationStatus) {
        return Result.ok(companyService.list(page, size, keyword, industry, city, verificationStatus));
    }

    @GetMapping("/{id}")
    public Result<CompanyDetailVO> detail(Authentication authentication,
                                          @PathVariable("id") Long id) {
        return Result.ok(companyService.detail(id, currentUserId(authentication),
                isAdmin(authentication)));
    }

    @GetMapping("/{id}/jobs")
    public Result<PageResult<JobSummaryVO>> jobs(
            Authentication authentication,
            @PathVariable("id") Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return Result.ok(companyService.jobs(id, page, size, currentUserId(authentication),
                isAdmin(authentication)));
    }

    @GetMapping("/{id}/platform-links")
    public Result<List<PlatformLinkVO>> platformLinks(@PathVariable("id") Long id) {
        return Result.ok(companyService.platformLinks(id));
    }

    @PostMapping("/{id}/verify")
    public Result<CompanyDetailVO> verify(Authentication authentication,
                                          @PathVariable("id") Long id,
                                          @RequestParam(defaultValue = "VERIFIED") String status) {
        return Result.ok(companyService.verify(id, status, currentUserId(authentication),
                isAdmin(authentication)));
    }

    /** JwtAuthFilter 把 userId 放进 principal，这里统一取出 */
    private static long currentUserId(Authentication authentication) {
        return Long.parseLong(String.valueOf(authentication.getPrincipal()));
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
