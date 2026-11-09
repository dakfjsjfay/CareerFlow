package com.team.resume.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.DocumentAnalysisResult;
import com.team.resume.common.BusinessException;
import com.team.resume.file.vo.FileAnalysisResponse;

@ExtendWith(MockitoExtension.class)
class FileAnalysisServiceTest {

    private static final String CONFIRM_WARNING = "分析结果来自上传文件，需用户确认后才可写入档案";

    @Mock
    private DocumentTextExtractor extractor;

    @Mock
    private AIService aiService;

    private FileAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new FileAnalysisService(extractor, aiService);
    }

    @Test
    void singleFileReturnsCandidatesAndConfirmationWarning() {
        MockMultipartFile file = file("project.docx", "project");
        when(extractor.extract(file)).thenReturn(
                new ExtractedDocument("project.docx", "DOCX", "项目正文", false, List.of("解析警告")));
        when(aiService.analyzeDocument("project.docx", "PROJECT", "JD 内容", "项目正文"))
                .thenReturn(aiResult());

        FileAnalysisResponse result = service.analyze(List.of(file), "project", "JD 内容");

        assertThat(result.files()).hasSize(1);
        assertThat(result.fileName()).isEqualTo("project.docx");
        assertThat(result.fileType()).isEqualTo("DOCX");
        assertThat(result.analysisType()).isEqualTo("PROJECT");
        assertThat(result.summary()).isEqualTo("项目总结");
        assertThat(result.resumeSuggestions()).containsExactly("简历描述");
        assertThat(result.jobGapSuggestions()).containsExactly("差距建议");
        assertThat(result.warnings()).contains("解析警告", "AI 警告", CONFIRM_WARNING);
    }

    @Test
    void combinesMultipleFilesIntoOneAiRequest() {
        MockMultipartFile first = file("a.md", "first");
        MockMultipartFile second = file("b.txt", "second");
        when(extractor.extract(first)).thenReturn(
                new ExtractedDocument("a.md", "MD", "第一段内容", false, List.of()));
        when(extractor.extract(second)).thenReturn(
                new ExtractedDocument("b.txt", "TXT", "第二段内容", false, List.of("文件提示")));
        when(aiService.analyzeDocument(anyString(), eq("PROFILE"), isNull(), anyString()))
                .thenReturn(aiResult());

        FileAnalysisResponse result = service.analyze(List.of(first, second), "PROFILE", null);

        assertThat(result.files()).hasSize(2);
        assertThat(result.fileType()).isEqualTo("MULTI");
        assertThat(result.fileName()).isEqualTo("a.md、b.txt");
        assertThat(result.warnings()).contains("b.txt：文件提示", CONFIRM_WARNING);

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiService).analyzeDocument(eq("a.md、b.txt"), eq("PROFILE"), isNull(),
                textCaptor.capture());
        assertThat(textCaptor.getValue())
                .contains("===== 文件 1：a.md =====")
                .contains("第一段内容")
                .contains("===== 文件 2：b.txt =====")
                .contains("第二段内容");
    }

    @Test
    void zipMustBeUploadedAlone() {
        MockMultipartFile zip = file("project.zip", "zip");
        MockMultipartFile other = file("readme.md", "readme");

        assertThatThrownBy(() -> service.analyze(List.of(zip, other), "AUTO", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
        verify(extractor, never()).extract(any());
        verify(aiService, never()).analyzeDocument(anyString(), anyString(), any(), anyString());
    }

    @Test
    void rejectsMoreThanSixFiles() {
        List<MultipartFile> files = java.util.stream.IntStream.range(0, 7)
                .mapToObj(i -> (MultipartFile) file("f" + i + ".txt", "content"))
                .toList();

        assertThatThrownBy(() -> service.analyze(files, "AUTO", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多上传 6 个文件");
        verify(extractor, never()).extract(any());
    }

    @Test
    void invalidAnalysisTypeFallsBackToAuto() {
        MockMultipartFile file = file("resume.md", "resume");
        when(extractor.extract(file)).thenReturn(
                new ExtractedDocument("resume.md", "MD", "正文", false, List.of()));
        when(aiService.analyzeDocument("resume.md", "AUTO", null, "正文"))
                .thenReturn(aiResult());

        FileAnalysisResponse result = service.analyze(List.of(file), "unknown", null);

        assertThat(result.analysisType()).isEqualTo("AUTO");
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static DocumentAnalysisResult aiResult() {
        return new DocumentAnalysisResult(
                "项目总结",
                new ObjectMapper().createObjectNode(),
                List.of("优势"),
                List.of("简历描述"),
                List.of("差距建议"),
                List.of("待补充"),
                List.of("AI 警告"));
    }
}
