package com.team.resume.job.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 岗位表实体（job）。
 * 岗位是公共资源：所有登录用户可读，不按 user_id 隔离（docs/database.md §8）。
 */
@Data
@TableName("job")
public class Job {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String company;

    private String position;

    private String city;

    private String salary;

    private String jdContent;

    /** MOCK（内置模拟）/ MANUAL（手动录入） */
    private String sourceType;

    private String externalUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
