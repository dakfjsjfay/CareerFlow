package com.team.resume.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新投递状态（docs/api.md §11）：note 为状态类反馈，写入 application_history。
 */
@Data
public class UpdateApplicationStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;

    @Size(max = 500, message = "备注不能超过 500 字")
    private String note;
}
