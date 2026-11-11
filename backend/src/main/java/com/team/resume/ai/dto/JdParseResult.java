package com.team.resume.ai.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * JD 解析结果（AI 输出结构校验后返回）。
 */
@Data
@AllArgsConstructor
public class JdParseResult {

    private String position;

    private List<Requirement> requirements;

    @Data
    @AllArgsConstructor
    public static class Requirement {

        private String skill;

        private String importance;

        private BigDecimal weight;
    }
}
