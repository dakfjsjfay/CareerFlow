package com.team.resume.auth.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.auth.dto.LoginRequest;
import com.team.resume.auth.dto.RegisterRequest;
import com.team.resume.auth.entity.SysUser;
import com.team.resume.auth.mapper.SysUserMapper;
import com.team.resume.auth.service.AuthService;
import com.team.resume.auth.vo.LoginResponse;
import com.team.resume.auth.vo.UserVO;
import com.team.resume.common.BusinessException;
import com.team.resume.common.Result;

import jakarta.validation.Valid;

/**
 * 认证接口（契约见 docs/api.md §4）：
 * POST /api/auth/register  注册（用户名重复 → 409）
 * POST /api/auth/login     登录（失败 → 401）
 * GET  /api/auth/me        当前用户（从 JWT 过滤器解析出的身份）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SysUserMapper userMapper;

    public AuthController(AuthService authService, SysUserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @GetMapping("/me")
    public Result<UserVO> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return Result.ok(new UserVO(user.getId(), user.getUsername(), user.getEmail()));
    }
}
