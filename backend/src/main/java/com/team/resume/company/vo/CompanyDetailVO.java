package com.team.resume.company.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 企业详情：列表字段 + 实习/社招入口、简介与关联岗位数。 */
@Data
public class CompanyDetailVO {

    private Long id;

    private String name;

    private String shortName;

    private String industry;

    private String companyType;

    private String scale;

    /** 总部城市（对应 company.city） */
    private String city;

    private String website;

    private String recruitmentUrl;

    private String campusRecruitUrl;

    private String internRecruitUrl;

    private String socialRecruitUrl;

    private String logoUrl;

    private String description;

    /** SEED / ADMIN_IMPORT / MANUAL */
    private String source;

    /** 信息来源链接 */
    private String sourceUrl;

    /** VERIFIED / PENDING / EXPIRED */
    private String verificationStatus;

    private LocalDateTime lastVerifiedAt;

    /** 当前用户可见的关联岗位数量 */
    private long jobCount;
}
