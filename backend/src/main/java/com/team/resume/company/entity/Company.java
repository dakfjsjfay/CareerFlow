package com.team.resume.company.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 企业信息库实体（company，表结构见 V2_0__base.sql + V3_0__company.sql）。
 * 公共数据，无 user_id；写接口仅管理员。
 */
@Data
@TableName("company")
public class Company {

    public static final String SOURCE_OFFICIAL = "OFFICIAL";
    public static final String SOURCE_MIGRATED = "MIGRATED";
    public static final String SOURCE_SEED = "SEED";
    public static final String SOURCE_ADMIN_IMPORT = "ADMIN_IMPORT";
    public static final String SOURCE_MANUAL = "MANUAL";

    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public static final String ACTIVE = "ACTIVE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企业全称 */
    private String name;

    /** 简称 / 品牌名 */
    private String shortName;

    private String industry;

    /** 企业类型：国企/民企/外企/合资 */
    private String companyType;

    /** 规模区间 */
    private String scale;

    /** 总部城市 */
    private String city;

    /** 企业官网 */
    private String website;

    /** 招聘官网（综合入口） */
    private String recruitmentUrl;

    /** 校招入口 */
    private String campusRecruitUrl;

    /** 实习入口 */
    private String internRecruitUrl;

    /** 社招入口 */
    private String socialRecruitUrl;

    private String logoUrl;

    private String description;

    /** ACTIVE / INACTIVE */
    private String status;

    /** SEED / ADMIN_IMPORT / MANUAL */
    private String source;

    /** 信息来源链接 */
    private String sourceUrl;

    /** 链接核验状态：VERIFIED / PENDING / EXPIRED */
    private String verificationStatus;

    /** 最近一次人工核验时间 */
    private LocalDateTime lastVerifiedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
