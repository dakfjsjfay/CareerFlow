package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.dto.CreateResumeRequest;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.service.ResumeService;
import com.team.resume.resume.service.ResumeVersionService;
import com.team.resume.resume.vo.ResumeDetailVO;
import com.team.resume.resume.vo.ResumeSummaryVO;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private ResumeVersionMapper versionMapper;

    @Mock
    private ResumeVersionService versionService;

    @Mock
    private com.team.resume.profile.service.ProfileService profileService;

    @Mock
    private com.team.resume.resume.service.ResumeContentAssembler assembler;

    @Mock
    private com.team.resume.resume.service.ResumeAiService aiService;

    @Mock
    private com.team.resume.resume.service.ResumeDocxExporter docxExporter;

    private ResumeService service;

    @BeforeEach
    void setUp() {
        service = new ResumeService(resumeMapper, versionMapper, versionService,
                profileService, assembler, aiService, docxExporter, new ObjectMapper());
    }

    @Test
    void createUsesDefaultName() {
        when(resumeMapper.insert(any(Resume.class))).thenAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(10L);
            return 1;
        });

        ResumeSummaryVO summary = service.create(7L, new CreateResumeRequest());

        assertEquals("我的简历", summary.resumeName());
        assertNull(summary.currentVersionId());
        verify(resumeMapper).insert(org.mockito.ArgumentMatchers.argThat((Resume resume) ->
                resume.getUserId() == 7L && "我的简历".equals(resume.getResumeName())));
    }

    @Test
    void createRejectsTooLongName() {
        CreateResumeRequest request = new CreateResumeRequest();
        request.setResumeName("x".repeat(101));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create(7L, request));

        assertEquals(400, error.getCode());
        verify(resumeMapper, never()).insert(any(Resume.class));
    }

    @Test
    void listMapsCurrentVersion() {
        Resume resume = resume(1L, 7L);
        resume.setCurrentVersionId(5L);
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume));
        when(versionMapper.selectById(5L)).thenReturn(version(5L, 1L, 2, "tpl-9"));

        List<ResumeSummaryVO> summaries = service.list(7L);

        assertEquals(1, summaries.size());
        assertEquals(2, summaries.get(0).currentVersionNumber());
        assertEquals("tpl-9", summaries.get(0).templateId());
    }

    @Test
    void detailReturnsCurrentVersion() {
        Resume resume = resume(1L, 7L);
        resume.setCurrentVersionId(5L);
        when(resumeMapper.selectById(1L)).thenReturn(resume);
        when(versionMapper.selectById(5L)).thenReturn(version(5L, 1L, 2, "tpl-9"));

        ResumeDetailVO detail = service.detail(7L, 1L);

        assertEquals(1L, detail.id());
        assertEquals(2, detail.currentVersionNumber());
        assertEquals("tpl-9", detail.currentVersion().templateId());
    }

    @Test
    void detailWithoutVersionIsAllowed() {
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, 7L));

        ResumeDetailVO detail = service.detail(7L, 1L);

        assertNull(detail.currentVersionId());
        assertNull(detail.currentVersion());
    }

    @Test
    void rejectsOtherUsersResume() {
        when(resumeMapper.selectById(1L)).thenReturn(resume(1L, 8L));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.detail(7L, 1L));

        assertEquals(403, error.getCode());
    }

    @Test
    void returns404WhenResumeMissing() {
        when(resumeMapper.selectById(1L)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.detail(7L, 1L));

        assertEquals(404, error.getCode());
    }

    private static Resume resume(Long id, Long userId) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(userId);
        resume.setResumeName("我的简历");
        resume.setUpdatedAt(LocalDateTime.now());
        return resume;
    }

    private static ResumeVersion version(Long id, Long resumeId, int number, String templateId) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setResumeId(resumeId);
        version.setVersionNumber(number);
        version.setGenerationReason("INITIAL");
        version.setContent(new ObjectMapper().valueToTree(
                Map.of("meta", Map.of("templateId", templateId))));
        version.setCreatedAt(LocalDateTime.now());
        return version;
    }
}
