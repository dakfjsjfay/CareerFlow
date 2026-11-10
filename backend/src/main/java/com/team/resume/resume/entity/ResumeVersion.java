package com.team.resume.resume.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

/**
 * 简历版本（resume_version）：content 为结构化 JSON，版本一旦写入永不覆盖。
 */
@Data
@TableName(value = "resume_version", autoResultMap = true)
public class ResumeVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Integer versionNumber;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode content;

    private String generationReason;

    private LocalDateTime createdAt;
}
