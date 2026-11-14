package com.team.resume.job.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 岗位表实体（job）。
 * 2.0 起岗位分公共与私有：公共岗位所有登录用户可读，私有岗位仅创建者与管理员可读；
 * 写操作（解析 JD、修改技能要求）仅管理员或岗位创建者。
 */
@Data
@TableName("job")
public class Job {

    public static final String VISIBILITY_PUBLIC = "PUBLIC";
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String company;

    /** 关联 company；旧数据为空，company 文本保留兜底 */
    private Long companyId;

    private String position;

    private String city;

    private String salary;

    private String jdContent;

    /** MOCK（内置模拟）/ MANUAL（手动录入） */
    private String sourceType;

    /** 创建者用户 ID；系统/种子数据为空 */
    private Long createdBy;

    /** PUBLIC / PRIVATE */
    private String visibility;

    private String externalUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
