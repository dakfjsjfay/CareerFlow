package com.team.resume.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.resume.common.BusinessException;
import com.team.resume.company.entity.Company;
import com.team.resume.company.mapper.CompanyMapper;
import com.team.resume.company.service.CompanyService;
import com.team.resume.company.vo.CompanyDetailVO;
import com.team.resume.company.vo.CompanySummaryVO;
import com.team.resume.company.vo.PlatformLinkVO;
import com.team.resume.common.PageResult;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JobSummaryVO;

/**
 * 企业信息库单测：
 * 列表只返回 ACTIVE、详情带可见岗位数、平台入口仅外部搜索页、核验仅管理员可写。
 */
@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyMapper companyMapper;

    @Mock
    private JobMapper jobMapper;

    @Mock
    private JobService jobService;

    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyMapper, jobMapper, jobService);
    }

    @Test
    void listMapsActiveCompaniesToSummary() {
        Company company = company(1L, "阿里巴巴集团", "阿里");
        when(companyMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Company> page = invocation.getArgument(0);
            page.setRecords(List.of(company));
            page.setTotal(1);
            return page;
        });

        PageResult<CompanySummaryVO> result = companyService.list(1, 10, "阿里", "互联网", "杭州", null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        CompanySummaryVO vo = result.getItems().get(0);
        assertThat(vo.getName()).isEqualTo("阿里巴巴集团");
        assertThat(vo.getShortName()).isEqualTo("阿里");
        assertThat(vo.getCity()).isEqualTo("杭州");
    }

    @Test
    void detailCountsVisibleJobs() {
        when(companyMapper.selectById(1L)).thenReturn(company(1L, "腾讯控股有限公司", "腾讯"));
        when(jobMapper.selectCount(any())).thenReturn(3L);

        CompanyDetailVO vo = companyService.detail(1L, 7L, false);

        assertThat(vo.getJobCount()).isEqualTo(3L);
        assertThat(vo.getName()).isEqualTo("腾讯控股有限公司");
    }

    @Test
    void detailMissingCompanyThrows404() {
        when(companyMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> companyService.detail(99L, 7L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("企业不存在");
    }

    @Test
    void jobsDelegatesToJobServiceWithCompanyFilter() {
        when(companyMapper.selectById(1L)).thenReturn(company(1L, "阿里巴巴集团", "阿里"));
        when(jobService.list(anyLong(), anyLong(), isNull(), isNull(), isNull(),
                eq(1L), isNull(), isNull(), eq(7L), eq(false)))
                .thenReturn(new PageResult<>(List.<JobSummaryVO>of(), 0, 1, 10));

        PageResult<JobSummaryVO> result = companyService.jobs(1L, 1, 10, 7L, false);

        assertThat(result.getItems()).isEmpty();
        verify(jobService).list(1, 10, null, null, null, 1L, null, null, 7L, false);
    }

    @Test
    void platformLinksAreExternalSearchPagesWithNoAutoApplyNote() {
        when(companyMapper.selectById(1L)).thenReturn(company(1L, "阿里巴巴集团", "阿里"));

        List<PlatformLinkVO> links = companyService.platformLinks(1L);

        assertThat(links).hasSize(6);
        assertThat(links).extracting(PlatformLinkVO::key)
                .containsExactly("BOSS直聘", "智联招聘", "前程无忧", "拉勾", "实习僧", "牛客");
        assertThat(links).allSatisfy(link -> {
            assertThat(link.url()).startsWith("https://");
            assertThat(link.url()).contains("%E9%98%BF%E9%87%8C");
            assertThat(link.note()).contains("不代投");
        });
    }

    @Test
    void nonAdminCannotVerify() {
        assertThatThrownBy(() -> companyService.verify(1L, "VERIFIED", 7L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅管理员");
        verify(companyMapper, never()).updateById(any(Company.class));
    }

    @Test
    void verifyRejectsUnknownStatus() {
        assertThatThrownBy(() -> companyService.verify(1L, "DONE", 1L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("VERIFIED");
    }

    @Test
    void adminVerifyUpdatesStatusAndTimestamp() {
        Company company = company(1L, "阿里巴巴集团", "阿里");
        when(companyMapper.selectById(1L)).thenReturn(company);
        when(companyMapper.updateById(any(Company.class))).thenAnswer(invocation -> {
            Company saved = invocation.getArgument(0);
            saved.setVerificationStatus(Company.STATUS_VERIFIED);
            return 1;
        });
        when(jobMapper.selectCount(any())).thenReturn(0L);

        CompanyDetailVO vo = companyService.verify(1L, "verified", 1L, true);

        assertThat(vo.getVerificationStatus()).isEqualTo(Company.STATUS_VERIFIED);
        assertThat(company.getLastVerifiedAt()).isNotNull();
        verify(companyMapper).updateById(company);
    }

    private static Company company(Long id, String name, String shortName) {
        Company company = new Company();
        company.setId(id);
        company.setName(name);
        company.setShortName(shortName);
        company.setIndustry("互联网");
        company.setScale("5000人以上");
        company.setCity("杭州");
        company.setWebsite("https://www.alibabagroup.com");
        company.setStatus(Company.ACTIVE);
        company.setSource(Company.SOURCE_SEED);
        company.setVerificationStatus(Company.STATUS_PENDING);
        return company;
    }
}
