package com.team.resume.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 发送消息请求（docs/api.md §5）。
 */
@Data
public class SendMessageRequest {

    @NotNull(message = "会话不能为空")
    private Long sessionId;

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "单条消息不能超过 4000 个字符")
    private String message;
}
