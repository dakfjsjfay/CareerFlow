package com.team.resume.optimization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 回复 AI 追问（docs/api.md §10）。
 */
@Data
public class ReplyOptimizationRequest {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 2000, message = "回答内容过长")
    private String answer;
}
