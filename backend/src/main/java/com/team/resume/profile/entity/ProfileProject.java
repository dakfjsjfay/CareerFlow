package com.team.resume.profile.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import lombok.Data;

/**
 * 项目经历（profile_project）：只保存用户确认后的真实项目。
 */
@Data
@TableName(value = "profile_project", autoResultMap = true)
public class ProfileProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long profileId;

    private String projectName;

    private String role;

    private String description;

    /** 该项目真实使用的技术栈（不能由「学过」推断）。 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> techStack;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
