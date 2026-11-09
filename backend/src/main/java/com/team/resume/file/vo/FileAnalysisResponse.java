package com.team.resume.file.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 文件分析返回（docs/api.md §15）。
 * 所有内容均为待确认候选，不直接写入档案。
 */
public record FileAnalysisResponse(
        List<FilePartInfo> files,
        String fileName,
        String fileType,
        String analysisType,
        boolean truncated,
        String summary,
        JsonNode extractedInfo,
        List<String> strengths,
        List<String> resumeSuggestions,
        List<String> jobGapSuggestions,
        List<String> missingFields,
        List<String> warnings) {
}
