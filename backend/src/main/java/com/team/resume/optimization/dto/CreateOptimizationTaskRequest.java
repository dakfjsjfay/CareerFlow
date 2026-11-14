package com.team.resume.optimization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建优化任务（docs/api.md §10）：点击匹配页差距项发起。
 * chatSessionId 可选：不传则自动新建一个追问会话（对话页内追问）。
 */
@Data
public class CreateOptimizationTaskRequest {

    @NotNull(message = "jobId 不能为空")
    private Long jobId;

    @NotNull(message = "resumeVersionId 不能为空")
    private Long resumeVersionId;

    @NotBlank(message = "目标技能不能为空")
    @Size(max = 100, message = "目标技能名称过长")
    private String targetSkill;

    private Long chatSessionId;
}
