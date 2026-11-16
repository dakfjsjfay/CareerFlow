package com.team.resume.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team.resume.ai.AIService;
import com.team.resume.common.BusinessException;
import com.team.resume.job.dto.CreateJobRequest;
import com.team.resume.job.dto.ImportJobRequest;
import com.team.resume.job.dto.ParseJdRequest;
import com.team.resume.job.dto.VerifyJobRequest;
import com.team.resume.job.entity.Job;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.mapper.JobRequirementMapper;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JobDetailVO;
import com.team.resume.job.vo.JobSummaryVO;

/**
 * 岗位归属与可见性单测（docs/api.md §8.0）：
 * 普通用户创建私有岗位、管理员创建公共岗位、他人私有岗位按不存在处理、无权解析 403。
 */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobMapper jobMapper;

    @Mock
    private JobRequirementMapper requirementMapper;

    @Mock
    private AIService aiService;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobMapper, requirementMapper, aiService);
    }

    @Test
    void normalUserCreatesPrivateJob() {
        when(jobMapper.insert(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(10L);
            return 1;
        });

        jobService.create(createRequest(), 7L, false);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobMapper).insert(captor.capture());
        Job saved = captor.getValue();
        assertThat(saved.getVisibility()).isEqualTo(Job.VISIBILITY_PRIVATE);
        assertThat(saved.getCreatedBy()).isEqualTo(7L);
    }

    @Test
    void adminCreatesPublicJob() {
        when(jobMapper.insert(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(11L);
            return 1;
        });

        jobService.create(createRequest(), 1L, true);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobMapper).insert(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(Job.VISIBILITY_PUBLIC);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(1L);
    }

    @Test
    void readingOthersPrivateJobReturns404() {
        when(jobMapper.selectById(5L)).thenReturn(job(5L, 99L, Job.VISIBILITY_PRIVATE));

        assertThatThrownBy(() -> jobService.detail(5L, 7L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void ownerSeesCanEditTrue() {
        when(jobMapper.selectById(6L)).thenReturn(job(6L, 7L, Job.VISIBILITY_PRIVATE));
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        JobDetailVO detail = jobService.detail(6L, 7L, false);

        assertThat(detail.getCanEdit()).isTrue();
    }

    @Test
    void parseWithoutWritePermissionReturns403() {
        when(jobMapper.selectById(7L)).thenReturn(job(7L, 99L, Job.VISIBILITY_PUBLIC));

        assertThatThrownBy(() -> jobService.parse(7L, new ParseJdRequest(), 7L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(aiService, never()).parseJd(any());
    }

    private static CreateJobRequest createRequest() {
        CreateJobRequest request = new CreateJobRequest();
        request.setPosition("Java 开发实习生");
        return request;
    }

    @Test
    void demoJobIsNeverShownAsVerified() {
        Job demo = job(20L, null, Job.VISIBILITY_PUBLIC);
        demo.setSourceType(Job.SOURCE_MOCK);
        demo.setVerificationStatus(Job.VERIFICATION_VERIFIED);
        when(jobMapper.selectById(20L)).thenReturn(demo);
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        JobDetailVO detail = jobService.detail(20L, 7L, false);

        assertThat(detail.getDisplayStatus()).isEqualTo(Job.VERIFICATION_DEMO);
    }

    @Test
    void closedRecruitmentOverridesVerificationInDisplayStatus() {
        Job closed = job(21L, null, Job.VISIBILITY_PUBLIC);
        closed.setSourceType(Job.SOURCE_OFFICIAL);
        closed.setVerificationStatus(Job.VERIFICATION_VERIFIED);
        closed.setRecruitmentStatus(Job.RECRUITMENT_CLOSED);
        when(jobMapper.selectById(21L)).thenReturn(closed);
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        assertThat(jobService.detail(21L, 7L, false).getDisplayStatus())
                .isEqualTo(Job.RECRUITMENT_CLOSED);
    }

    @Test
    void pastValidUntilIsShownAsExpired() {
        Job expired = job(22L, null, Job.VISIBILITY_PUBLIC);
        expired.setSourceType(Job.SOURCE_OFFICIAL);
        expired.setVerificationStatus(Job.VERIFICATION_VERIFIED);
        expired.setValidUntil(LocalDateTime.now().minusDays(1));
        when(jobMapper.selectById(22L)).thenReturn(expired);
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        assertThat(jobService.detail(22L, 7L, false).getDisplayStatus())
                .isEqualTo(Job.VERIFICATION_EXPIRED);
    }

    @Test
    void nonAdminCannotVerifyJob() {
        assertThatThrownBy(() -> jobService.verify(5L, verifyRequest("VERIFIED"), 7L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
    }

    @Test
    void demoJobCannotBeVerified() {
        Job demo = job(23L, null, Job.VISIBILITY_PUBLIC);
        demo.setSourceType(Job.SOURCE_DEMO);
        when(jobMapper.selectById(23L)).thenReturn(demo);

        assertThatThrownBy(() -> jobService.verify(23L, verifyRequest("VERIFIED"), 1L, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void verifyingWithoutSourceEvidenceIsRejected() {
        Job job = job(24L, null, Job.VISIBILITY_PUBLIC);
        job.setSourceType(Job.SOURCE_MANUAL);
        when(jobMapper.selectById(24L)).thenReturn(job);

        assertThatThrownBy(() -> jobService.verify(24L, verifyRequest("VERIFIED"), 1L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原招聘链接或来源名称");
    }

    @Test
    void adminCanVerifyWithSourceEvidence() {
        Job job = job(25L, null, Job.VISIBILITY_PUBLIC);
        job.setSourceType(Job.SOURCE_OFFICIAL);
        job.setExternalUrl("https://example.com/job/25");
        when(jobMapper.selectById(25L)).thenReturn(job);
        when(jobMapper.updateById(any(Job.class))).thenReturn(1);
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        JobDetailVO detail = jobService.verify(25L, verifyRequest("VERIFIED"), 1L, true);

        assertThat(job.getVerificationStatus()).isEqualTo(Job.VERIFICATION_VERIFIED);
        assertThat(job.getVerifiedAt()).isNotNull();
        assertThat(job.getVerifiedBy()).isEqualTo(1L);
        assertThat(detail.getDisplayStatus()).isEqualTo(Job.VERIFICATION_VERIFIED);
    }

    @Test
    void importedJobIsPrivateAndUnverified() {
        AtomicReference<Job> saved = new AtomicReference<>();
        when(jobMapper.insert(any(Job.class))).thenAnswer(invocation -> {
            Job job = invocation.getArgument(0);
            job.setId(30L);
            saved.set(job);
            return 1;
        });
        when(jobMapper.selectById(30L)).thenAnswer(invocation -> saved.get());
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        ImportJobRequest request = new ImportJobRequest();
        request.setPosition("Java 开发实习生");
        request.setExternalUrl("https://example.com/job/30");
        request.setJdContent("熟悉 Java、MySQL。");

        JobSummaryVO summary = jobService.importJob(request, 7L);

        Job imported = saved.get();
        assertThat(imported.getVisibility()).isEqualTo(Job.VISIBILITY_PRIVATE);
        assertThat(imported.getCreatedBy()).isEqualTo(7L);
        assertThat(imported.getSourceType()).isEqualTo(Job.SOURCE_MANUAL);
        assertThat(imported.getSourceName()).isEqualTo("用户粘贴");
        assertThat(imported.getVerificationStatus()).isEqualTo(Job.VERIFICATION_UNVERIFIED);
        assertThat(summary.getDisplayStatus()).isEqualTo(Job.VERIFICATION_UNVERIFIED);
    }

    private static VerifyJobRequest verifyRequest(String status) {
        VerifyJobRequest request = new VerifyJobRequest();
        request.setVerificationStatus(status);
        return request;
    }

    private static Job job(Long id, Long createdBy, String visibility) {
        Job job = new Job();
        job.setId(id);
        job.setPosition("Java 开发实习生");
        job.setCreatedBy(createdBy);
        job.setVisibility(visibility);
        return job;
    }
}
