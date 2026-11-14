package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.service.ProfileService;
import com.team.resume.resume.dto.ExportResumeRequest;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.service.ResumeAiService;
import com.team.resume.resume.service.ResumeContentAssembler;
import com.team.resume.resume.service.ResumeDocxExporter;
import com.team.resume.resume.service.ResumePdfExporter;
import com.team.resume.resume.service.ResumeService;
import com.team.resume.resume.service.ResumeVersionService;
import com.team.resume.resume.vo.ExportFile;
import com.team.resume.resume.vo.ResumeContent;

@ExtendWith(MockitoExtension.class)
class ResumeExportServiceTest {

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
    private ResumeDocxExporter docxExporter;
    @Mock
    private ResumePdfExporter pdfExporter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeService service;

    @BeforeEach
    void setUp() {
        service = new ResumeService(resumeMapper, versionMapper, versionService,
                draftMapper, editHistoryMapper, applicationMapper,
                profileService, assembler, aiService, docxExporter, pdfExporter, objectMapper);
    }

    @Test
    void exportRejectsMissingVersionId() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(7L, 1L, request(null, "DOCX")));

        assertEquals(400, error.getCode());
    }

    @Test
    void exportRejectsUnsupportedFormat() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(7L, 1L, request(1L, "TXT")));

        assertEquals(400, error.getCode());
    }

    @Test
    void exportUsesRequestedVersionNotCurrent() {
        Resume resume = resume();
        resume.setCurrentVersionId(3L);
        when(resumeMapper.selectById(1L)).thenReturn(resume);
        ResumeContent versionOne = content("版本一");
        when(versionService.requireOwned(7L, 1L, 1L))
                .thenReturn(version(1L, 1, versionOne));
        when(docxExporter.export(any(), anyString(), anyInt())).thenReturn(new byte[] {1, 2, 3});

        ExportFile file = service.export(7L, 1L, request(1L, "DOCX"));

        assertEquals("我的简历-v1.docx", file.filename());
        ArgumentCaptor<ResumeContent> captor = ArgumentCaptor.forClass(ResumeContent.class);
        verify(docxExporter).export(captor.capture(), eq("我的简历"), eq(1));
        assertEquals("版本一", captor.getValue().selfEvaluation());
    }

    @Test
    void exportPdfUsesPdfExporterAndPdfExtension() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        when(versionService.requireOwned(7L, 1L, 1L)).thenReturn(version(1L, 1, content("PDF")));
        when(pdfExporter.export(any(), anyString(), eq(1))).thenReturn(new byte[] {'%', 'P', 'D', 'F'});

        ExportFile file = service.export(7L, 1L, request(1L, "PDF"));

        assertEquals("我的简历-v1.pdf", file.filename());
        verify(pdfExporter).export(any(), eq("我的简历"), eq(1));
        verify(docxExporter, org.mockito.Mockito.never()).export(any(), anyString(), anyInt());
    }

    @Test
    void exportPropagatesVersionOwnershipFailure() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        when(versionService.requireOwned(7L, 1L, 9L))
                .thenThrow(new BusinessException(403, "无权访问该版本"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(7L, 1L, request(9L, "DOCX")));

        assertEquals(403, error.getCode());
    }

    @Test
    void exportRejectsCorruptedContent() {
        when(resumeMapper.selectById(1L)).thenReturn(resume());
        ResumeVersion broken = version(1L, 1, content("x"));
        broken.setContent(null);
        when(versionService.requireOwned(7L, 1L, 1L)).thenReturn(broken);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(7L, 1L, request(1L, "DOCX")));

        assertEquals(500, error.getCode());
    }

    private static ExportResumeRequest request(Long versionId, String format) {
        ExportResumeRequest request = new ExportResumeRequest();
        request.setVersionId(versionId);
        request.setFormat(format);
        return request;
    }

    private static Resume resume() {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(7L);
        resume.setResumeName("我的简历");
        return resume;
    }

    private ResumeVersion version(Long id, int number, ResumeContent content) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setResumeId(1L);
        version.setVersionNumber(number);
        version.setGenerationReason("INITIAL");
        version.setContent(objectMapper.valueToTree(content));
        return version;
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
}
