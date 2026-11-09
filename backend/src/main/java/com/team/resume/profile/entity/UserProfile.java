package com.team.resume.profile.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;

/**
 * 用户档案（user_profile）：只保存「已确认」的基础信息、教育经历、技能与求职意向。
 * skills 为 JSON 数组，用 JacksonTypeHandler 与 List&lt;String&gt; 互转。
 */
@Data
@TableName(value = "user_profile", autoResultMap = true)
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String realName;

    private String phone;

    private String school;

    private String major;

    private String degree;

    private Integer graduationYear;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> skills;

    private String targetPosition;

    private String targetCity;

    private String expectedSalary;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
