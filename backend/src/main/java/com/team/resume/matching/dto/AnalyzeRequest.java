package com.team.resume.matching.dto;

import jakarta.validation.constraints.NotNull;

import lombok.Data;

/**
 * 匹配分析请求（docs/api.md §9）。
 */
@Data
public class AnalyzeRequest {

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    @NotNull(message = "简历版本不能为空")
    private Long resumeVersionId;
}
