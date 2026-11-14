package com.team.resume.resume.edit.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

/**
 * 区块级修改历史（resume_edit_history）：AI 候选先落 PENDING，用户确认后才 APPLIED。
 */
@Data
@TableName(value = "resume_edit_history", autoResultMap = true)
public class ResumeEditHistory {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String SOURCE_AI = "AI";
    public static final String SOURCE_MANUAL = "MANUAL";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private Long draftId;

    private Long baseVersionId;

    private String sectionKey;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode beforeContent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode afterContent;

    private String source;

    private String status;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> warnings;

    private String promptSummary;

    private String model;

    private LocalDateTime createdAt;

    private LocalDateTime appliedAt;
}
