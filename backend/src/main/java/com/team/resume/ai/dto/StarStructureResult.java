package com.team.resume.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * STAR 结构化结果：只做表达润色，不得补写用户未填的要素。
 */
public record StarStructureResult(JsonNode star, String description, List<String> warnings) {
}
