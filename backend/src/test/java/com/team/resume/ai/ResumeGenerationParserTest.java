package com.team.resume.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.dto.ResumeGenerationResult;
import com.team.resume.common.BusinessException;

class ResumeGenerationParserTest {

    private final AIResponseParser parser = new AIResponseParser(new ObjectMapper());

    @Test
    void parsesSuggestionJson() {
        ResumeGenerationResult result = parser.parseResumeGeneration("""
                {"selfEvaluation":"熟悉 Java",
                 "experienceHighlights":[{"index":0,"highlights":["完成接口开发"]}],
                 "projectHighlights":[{"index":1,"highlights":["实现增删改查"]}]}
                """);

        assertEquals("熟悉 Java", result.selfEvaluation());
        assertEquals(0, result.experienceHighlights().get(0).index());
        assertEquals("完成接口开发", result.experienceHighlights().get(0).highlights().get(0));
        assertEquals(1, result.projectHighlights().get(0).index());
    }

    @Test
    void stripsMarkdownFence() {
        ResumeGenerationResult result = parser.parseResumeGeneration("""
                ```json
                {"selfEvaluation":"熟悉 Java","experienceHighlights":[],"projectHighlights":[]}
                ```
                """);
        assertEquals("熟悉 Java", result.selfEvaluation());
    }

    @Test
    void rejectsNonJson() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> parser.parseResumeGeneration("not json"));
        assertEquals(502, error.getCode());
    }

    @Test
    void ignoresInvalidHighlightEntries() {
        ResumeGenerationResult result = parser.parseResumeGeneration("""
                {"selfEvaluation":"","experienceHighlights":[{"index":"x","highlights":["a"]},
                 {"index":0,"highlights":[""]}],"projectHighlights":null}
                """);
        assertTrue(result.experienceHighlights().isEmpty());
        assertTrue(result.projectHighlights().isEmpty());
    }
}
