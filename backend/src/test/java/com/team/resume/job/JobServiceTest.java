package com.team.resume.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.JobRequirementsResult;
import com.team.resume.ai.dto.JobRequirementsResult.Requirement;
import com.team.resume.common.BusinessException;
import com.team.resume.common.PageVO;
import com.team.resume.job.dto.JdParseRequest;
import com.team.resume.job.dto.JobCreateRequest;
import com.team.resume.job.dto.JobRequirementRequest;
import com.team.resume.job.entity.Job;
import com.team.resume.job.entity.JobRequirement;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.mapper.JobRequirementMapper;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JdParseResultVO;
import com.team.resume.job.vo.JobDetailVO;
import com.team.resume.job.vo.JobSummaryVO;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobMapper jobMapper;

    @Mock
    private JobRequirementMapper requirementMapper;

    @Mock
    private AIService aiService;

    private JobService service;

    @BeforeEach
    void setUp() {
        service = new JobService(jobMapper, requirementMapper, aiService);
    }

    @Test
    void listsJobsWithPageMetadata() {
        when(jobMapper.selectCount(any())).thenReturn(2L);
        when(jobMapper.selectList(any())).thenReturn(List.of(job(1001L), job(1002L)));

        PageVO<JobSummaryVO> page = service.list(1, 10, null, null, null);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(10);
        assertThat(page.records()).hasSize(2);
        assertThat(page.records().get(0).position()).isEqualTo("Java 开发实习生");
        assertThat(page.records().get(0).company()).isEqualTo("星云科技");
    }

    @Test
    void rejectsInvalidPageOrSize() {
        assertThatThrownBy(() -> service.list(0, 10, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
        assertThatThrownBy(() -> service.list(1, 51, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);

        verifyNoInteractions(jobMapper);
    }

    @Test
    void skipsListQueryWhenNoJobsMatch() {
        when(jobMapper.selectCount(any())).thenReturn(0L);

        PageVO<JobSummaryVO> page = service.list(1, 10, "不存在", null, null);

        assertThat(page.records()).isEmpty();
        verify(jobMapper, never()).selectList(any());
    }

    @Test
    void returnsJobDetailWithRequirements() {
        when(jobMapper.selectById(1001L)).thenReturn(job(1001L));
        when(requirementMapper.selectList(any())).thenReturn(List.of(
                requirement(1L, 1001L, "Java", "REQUIRED", "60"),
                requirement(2L, 1001L, "MySQL", "PREFERRED", "40")));

        JobDetailVO detail = service.detail(1001L);

        assertThat(detail.id()).isEqualTo(1001L);
        assertThat(detail.requirements()).hasSize(2);
        assertThat(detail.requirements().get(0).skill()).isEqualTo("Java");
        assertThat(detail.requirements().get(0).weight()).isEqualByComparingTo("60");
    }

    @Test
    void returns404WhenJobMissing() {
        when(jobMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.detail(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void createsManualJobAndNormalizesRequirements() {
        when(jobMapper.insert(any(Job.class))).thenAnswer(invocation -> {
            Job inserted = invocation.getArgument(0);
            inserted.setId(1001L);
            return 1;
        });
        JobCreateRequest request = new JobCreateRequest();
        request.setCompany("星云科技");
        request.setPosition("Java 开发实习生");
        request.setCity("深圳");
        request.setRequirements(List.of(
                requirementRequest("Java", "REQUIRED", "1"),
                requirementRequest("MySQL", "REQUIRED", "1")));

        JobDetailVO detail = service.create(request);

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobMapper).insert(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getSourceType()).isEqualTo(Job.SOURCE_MANUAL);
        assertThat(jobCaptor.getValue().getCompany()).isEqualTo("星云科技");
        assertThat(detail.requirements()).hasSize(2);
        assertThat(detail.requirements().get(0).weight()).isEqualByComparingTo("50.00");
        assertThat(detail.requirements().get(1).weight()).isEqualByComparingTo("50.00");
        verify(requirementMapper, times(2)).insert(any(JobRequirement.class));
    }

    @Test
    void rejectsBlankCompanyOrPosition() {
        JobCreateRequest noCompany = new JobCreateRequest();
        noCompany.setCompany(" ");
        noCompany.setPosition("Java");
        assertThatThrownBy(() -> service.create(noCompany))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);

        JobCreateRequest noPosition = new JobCreateRequest();
        noPosition.setCompany("星云科技");
        noPosition.setPosition("");
        assertThatThrownBy(() -> service.create(noPosition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);

        verifyNoInteractions(jobMapper, requirementMapper, aiService);
    }

    @Test
    void parsesStoredJdAndReplacesRequirements() {
        when(jobMapper.selectById(1001L)).thenReturn(job(1001L));
        when(aiService.parseJobDescription("熟悉 Java 与 MySQL")).thenReturn(
                new JobRequirementsResult("Java 开发实习生", List.of(
                        new Requirement("Java", "REQUIRED", new BigDecimal("60")),
                        new Requirement("MySQL", "REQUIRED", new BigDecimal("40")))));

        JdParseResultVO result = service.parse(1001L, null);

        assertThat(result.position()).isEqualTo("Java 开发实习生");
        assertThat(result.requirements()).extracting(r -> r.skill())
                .containsExactly("Java", "MySQL");
        assertThat(result.requirements()).extracting(r -> r.weight())
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("40.00"));
        verify(requirementMapper).delete(any());
        verify(requirementMapper, times(2)).insert(any(JobRequirement.class));
        verify(jobMapper, never()).updateById(any(Job.class));
    }

    @Test
    void updatesJdBeforeParsingWhenRequestProvidesIt() {
        Job job = job(1001L);
        when(jobMapper.selectById(1001L)).thenReturn(job);
        when(aiService.parseJobDescription("新的 JD")).thenReturn(
                new JobRequirementsResult("Java 开发实习生",
                        List.of(new Requirement("Java", "REQUIRED", new BigDecimal("100")))));
        JdParseRequest request = new JdParseRequest();
        request.setJdContent("新的 JD");

        service.parse(1001L, request);

        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        verify(jobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getJdContent()).isEqualTo("新的 JD");
    }

    @Test
    void rejectsParseWhenJdIsBlank() {
        Job job = job(1001L);
        job.setJdContent(null);
        when(jobMapper.selectById(1001L)).thenReturn(job);

        assertThatThrownBy(() -> service.parse(1001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
        verifyNoInteractions(aiService);
        verify(requirementMapper, never()).delete(any());
    }

    @Test
    void propagatesAiFailureWithoutReplacingRequirements() {
        when(jobMapper.selectById(1001L)).thenReturn(job(1001L));
        when(aiService.parseJobDescription("熟悉 Java 与 MySQL"))
                .thenThrow(new BusinessException(502, "AI 失败"));

        assertThatThrownBy(() -> service.parse(1001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);
        verify(requirementMapper, never()).delete(any());
        verify(requirementMapper, never()).insert(any(JobRequirement.class));
    }

    @Test
    void rejectsParseWhenAiReturnsNoUsableRequirements() {
        when(jobMapper.selectById(1001L)).thenReturn(job(1001L));
        when(aiService.parseJobDescription("熟悉 Java 与 MySQL"))
                .thenReturn(new JobRequirementsResult("Java 开发实习生", List.of()));

        assertThatThrownBy(() -> service.parse(1001L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);
        verify(requirementMapper, never()).delete(any());
    }

    private static Job job(Long id) {
        Job job = new Job();
        job.setId(id);
        job.setCompany("星云科技");
        job.setPosition("Java 开发实习生");
        job.setCity("深圳");
        job.setSalary("200-300元/天");
        job.setJdContent("熟悉 Java 与 MySQL");
        job.setSourceType(Job.SOURCE_MOCK);
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return job;
    }

    private static JobRequirement requirement(Long id, Long jobId, String skill,
                                              String importance, String weight) {
        JobRequirement requirement = new JobRequirement();
        requirement.setId(id);
        requirement.setJobId(jobId);
        requirement.setSkill(skill);
        requirement.setImportance(importance);
        requirement.setWeight(new BigDecimal(weight));
        return requirement;
    }

    private static JobRequirementRequest requirementRequest(String skill, String importance, String weight) {
        JobRequirementRequest request = new JobRequirementRequest();
        request.setSkill(skill);
        request.setImportance(importance);
        request.setWeight(new BigDecimal(weight));
        return request;
    }
}
