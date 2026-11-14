package com.team.resume.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.team.resume.auth.dto.LoginRequest;
import com.team.resume.auth.dto.RegisterRequest;
import com.team.resume.auth.entity.SysUser;
import com.team.resume.auth.mapper.SysUserMapper;
import com.team.resume.auth.service.AuthService;
import com.team.resume.common.BusinessException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, passwordEncoder,
                new JwtUtil("unit-test-secret-key-0123456789abcdef"));
    }

    @Test
    void registerHashesPasswordAndSetsCreatedAt() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });

        RegisterRequest request = new RegisterRequest();
        request.setUsername("student01");
        request.setPassword("Password123");
        request.setEmail("student@example.com");

        authService.register(request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(passwordEncoder.matches("Password123", saved.getPasswordHash())).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateUsernameReturns409() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("student01");
        request.setPassword("Password123");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
        verify(userMapper, never()).insert(any(SysUser.class));
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("student01");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("student01");
        request.setPassword("WrongPassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(401);
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        SysUser user = new SysUser();
        user.setId(2L);
        user.setUsername("student01");
        user.setPasswordHash(passwordEncoder.encode("Password123"));
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("student01");
        request.setPassword("Password123");

        var response = authService.login(request);
        assertThat(response.getToken()).isNotBlank();
        // 旧数据没有角色时按普通用户返回，避免前端拿到 null
        assertThat(response.getUser().getRole()).isEqualTo(SysUser.ROLE_USER);
    }
}
