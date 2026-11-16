package com.team.resume.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team.resume.auth.JwtAuthFilter;
import com.team.resume.auth.JwtUtil;
import com.team.resume.common.BusinessException;
import com.team.resume.common.GlobalExceptionHandler;
import com.team.resume.config.SecurityConfig;
import com.team.resume.job.controller.JobController;
import com.team.resume.job.dto.ImportJobRequest;
import com.team.resume.job.dto.ParseJdRequest;
import com.team.resume.job.dto.VerifyJobRequest;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JobDetailVO;
import com.team.resume.job.vo.JobParseVO;
import com.team.resume.job.vo.JobSummaryVO;

/**
 * 岗位接口安全集成测试（MockMvc + 真实 Spring Security 过滤链）：
 * 覆盖未登录 401、管理端 403、普通用户创建私有岗位、无权解析 403。
 */
@WebMvcTest(controllers = JobController.class)
@ContextConfiguration(classes = {JobController.class, SecurityConfig.class,
        JwtAuthFilter.class, JwtUtil.class, GlobalExceptionHandler.class})
@Import({SecurityConfig.class, JwtAuthFilter.class, JwtUtil.class})
@TestPropertySource(properties = "jwt.secret=unit-test-secret-key-0123456789abcdef")
class JobControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private JobService jobService;

    @Test
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void normalUserCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/companies").header("Authorization", bearer("USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminPassesAdminSecurityRule() throws Exception {
        // 本轮尚无 /api/admin 控制器：通过安全链后落到 404，而非 403
        mockMvc.perform(get("/api/admin/companies").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void normalUserCanCreateJob() throws Exception {
        JobSummaryVO summary = new JobSummaryVO();
        summary.setId(1L);
        summary.setPosition("Java 开发实习生");
        when(jobService.create(any(), eq(1L), eq(false))).thenReturn(summary);

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":\"Java 开发实习生\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void normalUserCannotParseOthersJob() throws Exception {
        when(jobService.parse(anyLong(), any(ParseJdRequest.class), anyLong(), anyBoolean()))
                .thenThrow(new BusinessException(403, "无权修改该岗位"));

        mockMvc.perform(post("/api/jobs/1/parse")
                        .header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminCanParseJob() throws Exception {
        JobParseVO vo = new JobParseVO();
        vo.setJobId(1L);
        when(jobService.parse(anyLong(), any(ParseJdRequest.class), anyLong(), eq(true)))
                .thenReturn(vo);

        mockMvc.perform(post("/api/jobs/1/parse")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(1));
    }

    @Test
    void normalUserCannotVerifyJob() throws Exception {
        when(jobService.verify(anyLong(), any(VerifyJobRequest.class), anyLong(), eq(false)))
                .thenThrow(new BusinessException(403, "仅管理员可核验岗位"));

        mockMvc.perform(post("/api/jobs/1/verify")
                        .header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationStatus\":\"VERIFIED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void adminCanVerifyJob() throws Exception {
        JobDetailVO detail = new JobDetailVO();
        detail.setId(1L);
        detail.setDisplayStatus("VERIFIED");
        when(jobService.verify(anyLong(), any(VerifyJobRequest.class), anyLong(), eq(true)))
                .thenReturn(detail);

        mockMvc.perform(post("/api/jobs/1/verify")
                        .header("Authorization", bearer("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationStatus\":\"VERIFIED\",\"sourceName\":\"企业官网\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayStatus").value("VERIFIED"));
    }

    @Test
    void userCanImportExternalJob() throws Exception {
        JobSummaryVO summary = new JobSummaryVO();
        summary.setId(2L);
        summary.setDisplayStatus("UNVERIFIED");
        when(jobService.importJob(any(ImportJobRequest.class), eq(1L))).thenReturn(summary);

        mockMvc.perform(post("/api/jobs/import")
                        .header("Authorization", bearer("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":\"Java 开发实习生\",\"externalUrl\":\"https://example.com/job/2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.displayStatus").value("UNVERIFIED"));
    }

    private String bearer(String role) {
        return "Bearer " + jwtUtil.generate(1L, "student01", role);
    }
}
