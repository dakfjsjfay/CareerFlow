package com.team.resume.job.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 岗位（job）：内置职位库或开发期手动新增的岗位。
 * 岗位是全局数据，不属于某个用户；访问仍要求登录（Spring Security 统一校验 JWT）。
 */
@Data
@TableName("job")
public class Job {

    public static final String SOURCE_MOCK = "MOCK";
    public static final String SOURCE_MANUAL = "MANUAL";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String company;

    private String position;

    private String city;

    private String salary;

    private String jdContent;

    private String sourceType;

    private String externalUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
