package com.team.resume.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.resume.auth.JwtUtil;
import com.team.resume.auth.dto.LoginRequest;
import com.team.resume.auth.dto.RegisterRequest;
import com.team.resume.auth.entity.SysUser;
import com.team.resume.auth.mapper.SysUserMapper;
import com.team.resume.auth.vo.LoginResponse;
import com.team.resume.auth.vo.UserVO;
import com.team.resume.common.BusinessException;

/**
 * 认证业务：注册与登录。
 * 规则：密码只存 BCrypt 哈希；用户名唯一（重复 → 409）；登录失败统一返回 401「用户名或密码错误」
 * （不区分哪个错了，避免被人探测出哪些用户名存在）。
 */
@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public void register(RegisterRequest request) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (count > 0) {
            throw new BusinessException(409, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        userMapper.insert(user);
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        UserVO userVO = new UserVO(user.getId(), user.getUsername(), user.getEmail());
        return new LoginResponse(jwtUtil.generate(user.getId(), user.getUsername()), userVO);
    }
}
