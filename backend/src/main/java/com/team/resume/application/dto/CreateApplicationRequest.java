package com.team.resume.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建投递（docs/api.md §11）：模拟一键投递，必须指定简历版本。
 */
@Data
public class CreateApplicationRequest {

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    @NotNull(message = "简历版本不能为空")
    private Long resumeVersionId;
}
