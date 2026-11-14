package com.team.resume.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team.resume.ai.AIService;
import com.team.resume.common.BusinessException;
import com.team.resume.job.dto.CreateJobRequest;
import com.team.resume.job.dto.ParseJdRequest;
import com.team.resume.job.entity.Job;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.mapper.JobRequirementMapper;
import com.team.resume.job.service.JobService;
import com.team.resume.job.vo.JobDetailVO;

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

    private static Job job(Long id, Long createdBy, String visibility) {
        Job job = new Job();
        job.setId(id);
        job.setPosition("Java 开发实习生");
        job.setCreatedBy(createdBy);
        job.setVisibility(visibility);
        return job;
    }
}
