package com.team.resume.matching.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 匹配分析结果（docs/api.md §9 返回结构）。
 */
@Data
public class MatchAnalyzeVO {

    private Long matchResultId;

    private Long jobId;

    private Long resumeVersionId;

    private BigDecimal matchScore;

    private Integer matchedCount;

    private Integer partialCount;

    private Integer missingCount;

    private Integer unconfirmedCount;

    private List<MatchDetailVO> details;
}
