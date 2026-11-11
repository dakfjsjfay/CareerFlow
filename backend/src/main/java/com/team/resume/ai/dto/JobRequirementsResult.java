package com.team.resume.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * JD 解析结果（AI 原始输出）：position 可为空，requirements 由 Java 再做归一化与校验。
 */
public record JobRequirementsResult(String position, List<Requirement> requirements) {

    public record Requirement(String skill, String importance, BigDecimal weight) {
    }
}
