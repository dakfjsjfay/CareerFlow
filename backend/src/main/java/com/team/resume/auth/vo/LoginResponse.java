package com.team.resume.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功返回：JWT token + 用户信息（契约见 docs/api.md §4）。
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;

    private UserVO user;
}
