package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.service.ProfileService;
import com.team.resume.profile.vo.ProfileVO;
import com.team.resume.resume.dto.GenerateResumeRequest;
import com.team.resume.resume.dto.SaveVersionRequest;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.service.ResumeAiService;
import com.team.resume.resume.service.ResumeContentAssembler;
import com.team.resume.resume.service.ResumeService;
import com.team.resume.resume.service.ResumeVersionService;
import com.team.resume.resume.vo.ResumeContent;
import com.team.resume.resume.vo.ResumeGenerateResultVO;

@ExtendWith(MockitoExtension.class)
class ResumeGenerateServiceTest {

    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeVersionMapper versionMapper;
    @Mock
    private ResumeVersionService versionService;
    @Mock
    private com.team.resume.resume.draft.mapper.ResumeDraftMapper draftMapper;
    @Mock
    private com.team.resume.resume.edit.mapper.ResumeEditHistoryMapper editHistoryMapper;
    @Mock
    private com.team.resume.application.mapper.JobApplicationMapper applicationMapper;
    @Mock
    private ProfileService profileService;
    @Mock
    private ResumeContentAssembler assembler;
    @Mock
    private ResumeAiService aiService;

    @Mock
    private com.team.resume.resume.service.ResumeDocxExporter docxExporter;

    @Mock
    private com.team.resume.resume.service.ResumePdfExporter pdfExporter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeService service;

    @BeforeEach
    void setUp() {
        service = new ResumeService(resumeMapper, versionMapper, versionService,
                draftMapper, editHistoryMapper, applicationMapper,
                profileService, assembler, aiService, docxExporter, pdfExporter, objectMapper);
    }

    @Test
    void generateRequiresTemplateId() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        GenerateResumeRequest request = request(null, "INITIAL");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generate(7L, 1L, request));

        assertEquals(400, error.getCode());
        verify(versionService, never()).create(any(), any(), any(), anyString());
    }

    @Test
    void generateRejectsUnknownReason() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        GenerateResumeRequest request = request("tpl-1", "UNKNOWN");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.generate(7L, 1L, request));

        assertEquals(400, error.getCode());
    }

    @Test
    void generateOrchestratesAssemblerAiAndVersion() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        when(profileService.getProfile(7L)).thenReturn(profile());
        ResumeContent base = content("base");
        ResumeContent enhanced = content("enhanced");
        when(assembler.assemble(any(), anyString(), eq("tpl-1"), any(), eq("INITIAL"), isNull()))
                .thenReturn(base);
        when(aiService.enhance(eq(base), any(ProfileVO.class), anyList())).thenReturn(enhanced);
        when(versionService.create(7L, 1L, enhanced, "INITIAL")).thenReturn(version(enhanced));

        ResumeGenerateResultVO result = service.generate(7L, 1L, request("tpl-1", "INITIAL"));

        assertEquals(1L, result.resumeId());
        assertEquals(9L, result.versionId());
        assertEquals(1, result.versionNumber());
        assertEquals("enhanced", result.content().path("selfEvaluation").asText());
    }

    @Test
    void generateDoesNotCreateVersionWhenAiFails() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        when(profileService.getProfile(7L)).thenReturn(profile());
        ResumeContent base = content("base");
        when(assembler.assemble(any(), anyString(), eq("tpl-1"), any(), eq("INITIAL"), isNull()))
                .thenReturn(base);
        when(aiService.enhance(eq(base), any(ProfileVO.class), anyList()))
                .thenThrow(new BusinessException(502, "AI 失败"));

        assertThrows(BusinessException.class,
                () -> service.generate(7L, 1L, request("tpl-1", "INITIAL")));

        verify(versionService, never()).create(any(), any(), any(), anyString());
    }

    @Test
    void saveVersionRejectsNullContent() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        SaveVersionRequest request = new SaveVersionRequest();
        request.setContent(null);
        request.setTemplateId("tpl-1");
        request.setGenerationReason("MANUAL_EDIT");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveVersion(7L, 1L, request));

        assertEquals(400, error.getCode());
    }

    @Test
    void saveVersionOverridesMetaAndCreatesNewVersion() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        JsonNode content = objectMapper.valueToTree(content("manual"));
        SaveVersionRequest request = new SaveVersionRequest();
        request.setContent(content);
        request.setTemplateId("tpl-new");
        request.setGenerationReason("TEMPLATE_SWITCH");

        when(versionService.create(eq(7L), eq(1L), any(ResumeContent.class), eq("TEMPLATE_SWITCH")))
                .thenAnswer(invocation -> version(invocation.getArgument(2)));

        service.saveVersion(7L, 1L, request);

        ArgumentCaptor<ResumeContent> captor = ArgumentCaptor.forClass(ResumeContent.class);
        verify(versionService).create(eq(7L), eq(1L), captor.capture(), eq("TEMPLATE_SWITCH"));
        ResumeContent saved = captor.getValue();
        assertEquals("tpl-new", saved.meta().templateId());
        assertEquals("MANUAL", saved.meta().source());
        assertEquals("TEMPLATE_SWITCH", saved.meta().generationReason());
        assertEquals("manual", saved.selfEvaluation());
    }

    @Test
    void saveVersionRejectsUnknownReason() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        SaveVersionRequest request = new SaveVersionRequest();
        request.setContent(objectMapper.valueToTree(content("manual")));
        request.setTemplateId("tpl-1");
        request.setGenerationReason("INITIAL");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.saveVersion(7L, 1L, request));

        assertEquals(400, error.getCode());
    }

    private static GenerateResumeRequest request(String templateId, String reason) {
        GenerateResumeRequest request = new GenerateResumeRequest();
        request.setTemplateId(templateId);
        request.setGenerationReason(reason);
        return request;
    }

    private static Resume resume() {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(7L);
        resume.setResumeName("我的简历");
        return resume;
    }

    private static ProfileVO profile() {
        return new ProfileVO(1L, "张同学", null, "深圳大学", "软件工程", "本科", 2028,
                List.of("Java"), "Java 开发实习生", "深圳", "面议", List.of(), List.of());
    }

    private static ResumeContent content(String selfEvaluation) {
        return new ResumeContent(
                new ResumeContent.Meta("tpl-1", "模板一", "2026-11-10T12:00:00", "AI",
                        "INITIAL", null, new ArrayList<>()),
                new ResumeContent.PersonalInfo("张同学", null, null, null, null,
                        "深圳大学", "软件工程", "本科", 2028, 0, null),
                new ResumeContent.JobIntention("Java 开发实习生", "深圳", "面议"),
                List.of(), List.of("Java"), List.of(), List.of(), selfEvaluation);
    }

    private ResumeVersion version(ResumeContent content) {
        ResumeVersion version = new ResumeVersion();
        version.setId(9L);
        version.setResumeId(1L);
        version.setVersionNumber(1);
        version.setGenerationReason(content.meta().generationReason());
        version.setContent(objectMapper.valueToTree(content));
        return version;
    }
}
