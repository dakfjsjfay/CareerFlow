package com.team.resume.chat.dto;

import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 创建会话请求：title 可选，不传时使用默认标题。
 */
@Data
public class CreateSessionRequest {

    @Size(max = 200, message = "标题长度不能超过 200 个字符")
    private String title;
}
