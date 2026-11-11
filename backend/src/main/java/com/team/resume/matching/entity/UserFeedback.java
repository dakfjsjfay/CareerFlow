package com.team.resume.matching.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 用户反馈表（user_feedback）——推荐排序的后置规则依据。
 * 实体暂放 matching 模块（推荐逻辑需要）；反馈接口在反馈分析步骤落地后迁移。
 */
@Data
@TableName("user_feedback")
public class UserFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    /** NOT_INTERESTED / LOCATION_MISMATCH / SALARY_MISMATCH / SKILL_ALREADY_HAVE */
    private String feedbackType;

    private String feedbackContent;

    private LocalDateTime createdAt;
}
