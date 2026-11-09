package com.team.resume.profile.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 工作/实习经历（profile_experience）：只保存用户确认后的真实经历。
 */
@Data
@TableName("profile_experience")
public class ProfileExperience {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long profileId;

    private String company;

    private String position;

    private String description;

    private String achievements;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
