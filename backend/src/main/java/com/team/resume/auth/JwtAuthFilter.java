package com.team.resume.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JWT 认证过滤器：每个请求进来时先经过这里。
 * 从 Authorization: Bearer xxx 头中取出令牌 → 校验 → 把用户身份与角色放进
 * SecurityContext（相当于告诉 Spring「这个请求是 userId=xxx、角色=xxx 的用户发的」）。
 * 令牌无效时不直接报错，保持未登录状态，由 Security 的 entryPoint 统一返回 401。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String DEFAULT_ROLE = "USER";

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                // 旧 token 没有 role claim，按 USER 处理，避免升级后强制重新登录
                String role = claims.get("role", String.class);
                String authority = "ROLE_" + (role == null || role.isBlank() ? DEFAULT_ROLE : role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null,
                                List.of(new SimpleGrantedAuthority(authority)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ignored) {
                // 令牌无效/过期：保持未认证状态，交给后续的 401 统一处理
            }
        }
        filterChain.doFilter(request, response);
    }
}
