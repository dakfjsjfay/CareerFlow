package com.team.resume.resume.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 简历主体（resume）：一个用户可以有多份简历，current_version_id 指向当前版本。
 */
@Data
@TableName("resume")
public class Resume {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String resumeName;

    private Long currentVersionId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
