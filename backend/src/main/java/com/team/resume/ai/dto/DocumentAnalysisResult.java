package com.team.resume.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 文件分析的结构化结果。
 * 只是候选信息，未经过用户确认前不得写入档案/简历。
 */
public record DocumentAnalysisResult(String summary, JsonNode extractedInfo,
                                     List<String> strengths, List<String> resumeSuggestions,
                                     List<String> jobGapSuggestions, List<String> missingFields,
                                     List<String> warnings) {
}
