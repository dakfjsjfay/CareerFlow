package com.team.resume.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 一次回复的结构化结果：自然语言回复 + 抽取候选信息 + 缺失字段。
 * 注意：extractedInfo 只是「候选信息」，D4 起必须经用户确认后才能写入档案。
 */
public record ChatExtractionResult(String reply, JsonNode extractedInfo, List<String> missingFields) {
}
