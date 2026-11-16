package com.team.resume.company;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team.resume.auth.JwtAuthFilter;
import com.team.resume.auth.JwtUtil;
import com.team.resume.common.BusinessException;
import com.team.resume.common.GlobalExceptionHandler;
import com.team.resume.common.PageResult;
import com.team.resume.company.controller.CompanyController;
import com.team.resume.company.service.CompanyService;
import com.team.resume.company.vo.CompanyDetailVO;
import com.team.resume.company.vo.CompanySummaryVO;
import com.team.resume.company.vo.PlatformLinkVO;
import com.team.resume.config.SecurityConfig;

/**
 * 企业库接口安全集成测试：
 * 未登录 401、普通用户可读、普通用户核验 403、管理员核验 200。
 */
@WebMvcTest(controllers = CompanyController.class)
@ContextConfiguration(classes = {CompanyController.class, SecurityConfig.class,
        JwtAuthFilter.class, JwtUtil.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
@TestPropertySource(properties = "jwt.secret=unit-test-secret-key-0123456789abcdef")
class CompanyControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private CompanyService companyService;

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void normalUserCanListCompanies() throws Exception {
        CompanySummaryVO summary = new CompanySummaryVO();
        summary.setId(1L);
        summary.setName("阿里巴巴集团");
        when(companyService.list(1, 10, "阿里", null, null, null))
                .thenReturn(new PageResult<>(List.of(summary), 1, 1, 10));

        mockMvc.perform(get("/api/companies?keyword=阿里").header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("阿里巴巴集团"));
    }

    @Test
    void normalUserCanReadPlatformLinks() throws Exception {
        when(companyService.platformLinks(1L)).thenReturn(List.of(
                new PlatformLinkVO("BOSS直聘", "BOSS直聘",
                        "https://www.zhipin.com/web/geek/job?query=test", "跳转到该平台搜索页")));

        mockMvc.perform(get("/api/companies/1/platform-links").header("Authorization", bearer("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].key").value("BOSS直聘"))
                .andExpect(jsonPath("$.data[0].note").value("跳转到该平台搜索页"));
    }

    @Test
    void normalUserCannotVerifyCompany() throws Exception {
        when(companyService.verify(anyLong(), any(), anyLong(), anyBoolean()))
                .thenThrow(new BusinessException(403, "仅管理员可核实企业信息"));

        mockMvc.perform(post("/api/companies/1/verify").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminCanVerifyCompany() throws Exception {
        CompanyDetailVO vo = new CompanyDetailVO();
        vo.setId(1L);
        vo.setVerificationStatus("VERIFIED");
        when(companyService.verify(eq(1L), eq("VERIFIED"), anyLong(), eq(true))).thenReturn(vo);

        mockMvc.perform(post("/api/companies/1/verify").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationStatus").value("VERIFIED"));
    }

    private String bearer(String role) {
        return "Bearer " + jwtUtil.generate(1L, "student01", role);
    }
}
