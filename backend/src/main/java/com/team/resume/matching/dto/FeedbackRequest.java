package com.team.resume.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 岗位偏好反馈（docs/api.md §12）：与状态类反馈（application_history.note）区分。
 */
@Data
public class FeedbackRequest {

    @NotNull(message = "岗位不能为空")
    private Long jobId;

    @NotBlank(message = "反馈类型不能为空")
    private String feedbackType;

    @Size(max = 500, message = "补充说明不能超过 500 字")
    private String feedbackContent;
}
