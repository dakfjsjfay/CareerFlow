package com.team.resume.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.common.BusinessException;

class AIResponseParserTest {

    private final AIResponseParser parser = new AIResponseParser(new ObjectMapper());

    @Test
    void parsesValidStructuredResult() {
        String json = """
                {
                  "reply": "你负责了项目中的哪些功能？",
                  "extractedInfo": {
                    "skills": ["Java", "MySQL"],
                    "projects": [{"name": "学生信息管理系统"}]
                  },
                  "missingFields": ["个人负责模块", "项目开发时间"]
                }
                """;

        ChatExtractionResult result = parser.parseChatResult(json);

        assertThat(result.reply()).isEqualTo("你负责了项目中的哪些功能？");
        assertThat(result.extractedInfo().path("skills").get(0).asText()).isEqualTo("Java");
        assertThat(result.extractedInfo().path("projects").get(0).path("name").asText())
                .isEqualTo("学生信息管理系统");
        assertThat(result.missingFields()).containsExactly("个人负责模块", "项目开发时间");
    }

    @Test
    void stripsMarkdownCodeFence() {
        String content = "```json\n{\"reply\":\"好的\",\"extractedInfo\":{},\"missingFields\":[]}\n```";

        ChatExtractionResult result = parser.parseChatResult(content);

        assertThat(result.reply()).isEqualTo("好的");
        assertThat(result.missingFields()).isEmpty();
    }

    @Test
    void missingExtractedInfoFallsBackToEmptyObject() {
        ChatExtractionResult result = parser.parseChatResult(
                "{\"reply\":\"请继续介绍\",\"missingFields\":[]}");

        assertThat(result.extractedInfo().isObject()).isTrue();
        assertThat(result.extractedInfo().isEmpty()).isTrue();
    }

    @Test
    void invalidJsonReturns502() {
        assertThatThrownBy(() -> parser.parseChatResult("这不是 JSON"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);
    }

    @Test
    void missingReplyReturns502() {
        assertThatThrownBy(() -> parser.parseChatResult("{\"extractedInfo\":{},\"missingFields\":[]}"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);
    }

    @Test
    void parsesDocumentAnalysisResult() {
        String json = """
                {
                  "summary": "项目是一个学生信息管理系统",
                  "extractedInfo": {"skills": ["Java", "MySQL"], "projects": [{"name": "学生信息管理系统"}]},
                  "strengths": ["独立完成增删改查"],
                  "resumeSuggestions": ["基于 Java 实现学生信息管理系统"],
                  "jobGapSuggestions": ["JD 要求 Spring Boot，资料中未体现"],
                  "missingFields": ["项目开发时间"],
                  "warnings": ["项目职责描述较少"]
                }
                """;

        var result = parser.parseDocumentAnalysis(json);

        assertThat(result.summary()).isEqualTo("项目是一个学生信息管理系统");
        assertThat(result.extractedInfo().path("skills").get(0).asText()).isEqualTo("Java");
        assertThat(result.strengths()).containsExactly("独立完成增删改查");
        assertThat(result.resumeSuggestions()).containsExactly("基于 Java 实现学生信息管理系统");
        assertThat(result.jobGapSuggestions()).containsExactly("JD 要求 Spring Boot，资料中未体现");
        assertThat(result.missingFields()).containsExactly("项目开发时间");
        assertThat(result.warnings()).containsExactly("项目职责描述较少");
    }

    @Test
    void documentAnalysisDefaultsMissingFields() {
        var result = parser.parseDocumentAnalysis("{\"summary\":\"\",\"extractedInfo\":{}}");

        assertThat(result.summary()).isEqualTo("已完成文件分析");
        assertThat(result.strengths()).isEmpty();
        assertThat(result.resumeSuggestions()).isEmpty();
        assertThat(result.jobGapSuggestions()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void ignoresNonTextMissingFields() {
        ChatExtractionResult result = parser.parseChatResult("""
                {"reply":"请补充", "extractedInfo":{}, "missingFields":["项目时间", 123, null, " "]}
                """);

        assertThat(result.missingFields()).containsExactly("项目时间");
    }
}
