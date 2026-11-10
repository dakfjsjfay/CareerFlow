package com.team.resume.ai.dto;

import java.util.List;

/**
 * AI 简历文案建议（docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md §8.3）。
 * 只包含可改写的文案，事实字段仍由 Java 组装。
 */
public record ResumeGenerationResult(
        String selfEvaluation,
        List<Highlight> experienceHighlights,
        List<Highlight> projectHighlights) {

    public record Highlight(int index, List<String> highlights) {
    }
}
