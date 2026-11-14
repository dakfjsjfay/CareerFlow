package com.team.resume.ai.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单区块改写候选：afterContent 必须与原区块结构一致，warnings 记录事实校验情况。
 */
public record SectionRewriteResult(JsonNode afterContent, List<String> warnings) {
}
