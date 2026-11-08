package com.team.resume.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 工具：生成与校验登录令牌。
 *
 * 通俗理解：登录成功后，后端发一张「通行证」（token）给前端；
 * 前端之后的每个请求都带上它，后端校验通过才放行——这就是无状态认证，
 * 服务器不需要记住谁登录了。
 */
@Component
public class JwtUtil {

    private final SecretKey key;

    /** 令牌有效期：7 天 */
    private static final long EXPIRE_MILLIS = 7L * 24 * 3600 * 1000;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        // HS256 算法要求密钥至少 32 字节
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRE_MILLIS))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验令牌。无效/过期/被篡改都会抛异常，由调用方决定如何处理。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
