package com.team.resume.matching.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 匹配结果详情（GET /api/match/{id}）。
 */
@Data
public class MatchResultVO {

    private Long matchResultId;

    private Long jobId;

    private Long resumeVersionId;

    private BigDecimal matchScore;

    private List<MatchDetailVO> details;
}
