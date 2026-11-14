package com.team.resume.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 反向优化追问轮次的 AI 输出：
 * reply 为自然语言回复（追问或总结），nextQuestion 表示是否需要继续追问。
 */
public record OptimizationReplyResult(String reply, JsonNode extractedInfo,
                                      boolean nextQuestion, List<String> missingFields) {
}
