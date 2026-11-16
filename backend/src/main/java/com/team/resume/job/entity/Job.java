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

    public static final String SOURCE_DEMO = "DEMO";
    /** 旧版演示数据取值，迁移后不再写入，读取时仍按演示数据处理 */
    public static final String SOURCE_MOCK = "MOCK";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_OFFICIAL = "OFFICIAL";
    public static final String SOURCE_PLATFORM = "PLATFORM";
    public static final String SOURCE_IMPORT = "IMPORT";

    public static final String VERIFICATION_DEMO = "DEMO";
    public static final String VERIFICATION_UNVERIFIED = "UNVERIFIED";
    public static final String VERIFICATION_VERIFIED = "VERIFIED";
    public static final String VERIFICATION_EXPIRED = "EXPIRED";

    public static final String RECRUITMENT_OPEN = "OPEN";
    public static final String RECRUITMENT_CLOSED = "CLOSED";
    public static final String RECRUITMENT_SOON = "SOON";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String company;

    /** 关联 company；旧数据为空，company 文本保留兜底 */
    private Long companyId;

    private String position;

    private String city;

    private String salary;

    private String jdContent;

    /** DEMO（演示，不可当作真实招聘）/ MANUAL（手动录入）/ OFFICIAL / PLATFORM / IMPORT */
    private String sourceType;

    /** 来源名称，如「企业官网」「用户粘贴」 */
    private String sourceName;

    /** 原招聘信息发布时间 */
    private LocalDateTime publishedAt;

    /** 招聘批次：秋招 / 春招 / 实习 / 社招 */
    private String recruitmentBatch;

    /** 招聘状态：OPEN / CLOSED / SOON */
    private String recruitmentStatus;

    /** 数据可信度：DEMO / UNVERIFIED / VERIFIED / EXPIRED（与招聘状态相互独立） */
    private String verificationStatus;

    /** 最近核验时间 */
    private LocalDateTime verifiedAt;

    /** 核验管理员用户 ID，便于审计 */
    private Long verifiedBy;

    /** 招聘信息有效期截止 */
    private LocalDateTime validUntil;

    /** 创建者用户 ID；系统/种子数据为空 */
    private Long createdBy;

    /** PUBLIC / PRIVATE */
    private String visibility;

    private String externalUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
