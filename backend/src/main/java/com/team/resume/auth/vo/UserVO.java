package com.team.resume.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 返回给前端的用户信息（只暴露安全字段，绝不含 passwordHash）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long id;

    private String username;

    private String email;

    /** 角色：USER / ADMIN */
    private String role;
}
