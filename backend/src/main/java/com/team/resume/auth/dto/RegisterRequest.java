package com.team.resume.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 注册请求（校验规则与 docs/api.md §4 一致）：
 * username 3~50 字符必填；password ≥8 字符必填；email 可选但格式需正确。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度需在 3~50 之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码至少 8 个字符")
    private String password;

    @Email(message = "邮箱格式不正确")
    private String email;
}
