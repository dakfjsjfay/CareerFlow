package com.team.resume.matching.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 投递建议请求（docs/api.md §9.2）：匹配度分析 + AI 投递话术。
 */
@Data
public class AdviceRequest {

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    @NotNull(message = "简历版本不能为空")
    private Long resumeVersionId;
}
