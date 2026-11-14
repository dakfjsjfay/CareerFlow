package com.team.resume.auth.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户表实体（sys_user）。
 * passwordHash 是 BCrypt 加密后的密码，数据库中永远不存明文。
 */
@Data
@TableName("sys_user")
public class SysUser {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    /** 角色：USER / ADMIN（2.0 起用于管理端与岗位写权限判定） */
    private String role;

    private String email;

    private LocalDateTime createdAt;
}
