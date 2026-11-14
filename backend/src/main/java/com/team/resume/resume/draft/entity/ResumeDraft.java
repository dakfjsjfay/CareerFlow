package com.team.resume.resume.draft.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

/**
 * 八步创建草稿（resume_draft）：可反复覆盖的编辑态，与不可变的 resume_version 分离。
 */
@Data
@TableName(value = "resume_draft", autoResultMap = true)
public class ResumeDraft {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ABANDONED = "ABANDONED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long resumeId;

    private String draftName;

    private Integer currentStep;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode content;

    /** 乐观锁版本：每次保存 +1，前端提交旧值即冲突 */
    private Integer revision;

    private String status;

    private LocalDateTime lastSavedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
