package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.ResumeGenerationResult;
import com.team.resume.ai.dto.ResumeGenerationResult.Highlight;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.vo.ProfileVO;
import com.team.resume.resume.service.ResumeAiService;
import com.team.resume.resume.vo.ResumeContent;

/**
 * AI 只允许改写文案：任何在档案/基础内容里找不到依据的句子都必须被丢弃。
 */
@ExtendWith(MockitoExtension.class)
class ResumeAiServiceTest {

    @Mock
    private AIService aiService;

    private ResumeAiService service;

    @BeforeEach
    void setUp() {
        service = new ResumeAiService(aiService, new ObjectMapper());
    }

    @Test
    void appliesOnlyGroundedHighlights() {
        when(aiService.generateResume(anyString())).thenReturn(new ResumeGenerationResult(
                "熟悉 Java",
                List.of(new Highlight(0, List.of("完成接口开发", "主导百万用户系统"))),
                List.of()));

        List<String> warnings = new ArrayList<>();
        ResumeContent result = service.enhance(baseContent(), profile(), warnings);

        assertEquals(List.of("完成接口开发"), result.experiences().get(0).highlights());
        assertTrue(warnings.contains("AI_FACT_CHECK_FALLBACK"));
        assertEquals("熟悉 Java", result.selfEvaluation());
        assertTrue(result.personalInfo().abstractText().startsWith("熟悉 Java"));
    }

    @Test
    void fallsBackWhenSelfEvaluationIsUngrounded() {
        when(aiService.generateResume(anyString())).thenReturn(new ResumeGenerationResult(
                "主导百万用户系统", List.of(), List.of()));

        List<String> warnings = new ArrayList<>();
        ResumeContent result = service.enhance(baseContent(), profile(), warnings);

        assertEquals("熟悉 Java，完成接口开发。", result.selfEvaluation());
        assertTrue(warnings.contains("AI_FACT_CHECK_FALLBACK"));
    }

    @Test
    void appliesGroundedProjectHighlights() {
        when(aiService.generateResume(anyString())).thenReturn(new ResumeGenerationResult(
                "熟悉 Java",
                List.of(),
                List.of(new Highlight(0, List.of("实现增删改查")))));

        ResumeContent result = service.enhance(baseContent(), profile(), new ArrayList<>());

        assertEquals(List.of("实现增删改查"), result.projects().get(0).highlights());
    }

    @Test
    void propagatesAiFailure() {
        when(aiService.generateResume(anyString()))
                .thenThrow(new BusinessException(502, "AI 失败"));

        assertThrows(BusinessException.class,
                () -> service.enhance(baseContent(), profile(), new ArrayList<>()));
    }

    private static ResumeContent baseContent() {
        return new ResumeContent(
                new ResumeContent.Meta("tpl-1", "模板一", "2026-11-10T12:00:00", "AI",
                        "INITIAL", null, new ArrayList<>()),
                new ResumeContent.PersonalInfo("张同学", null, null, null, null,
                        "深圳大学", "软件工程", "本科", 2028, 0, "熟悉 Java"),
                new ResumeContent.JobIntention("Java 开发实习生", "深圳", "面议"),
                List.of(),
                List.of("Java"),
                List.of(new ResumeContent.Project("学生系统", "后端", "实现增删改查",
                        List.of("Java"), List.of(), null, null)),
                List.of(new ResumeContent.Experience("某公司", "后端实习生", "INTERNSHIP",
                        "完成接口开发", null, List.of(), null, null)),
                "熟悉 Java，完成接口开发。");
    }

    private static ProfileVO profile() {
        return new ProfileVO(1L, "张同学", null, "深圳大学", "软件工程", "本科", 2028,
                List.of("Java"), "Java 开发实习生", "深圳", "面议",
                List.of(new ProfileVO.ProjectVO(1L, "学生系统", "后端", "实现增删改查",
                        List.of("Java"), null, null)),
                List.of(new ProfileVO.ExperienceVO(1L, "某公司", "后端实习生",
                        "完成接口开发", null, null, null)));
    }
}
