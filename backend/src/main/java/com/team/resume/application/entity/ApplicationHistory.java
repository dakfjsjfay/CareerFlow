package com.team.resume.application.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 投递状态历史（application_history）：每次状态流转一条，note 承载状态类反馈。
 */
@Data
@TableName("application_history")
public class ApplicationHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long applicationId;

    private String oldStatus;

    private String newStatus;

    private String note;

    private LocalDateTime createdAt;
}
