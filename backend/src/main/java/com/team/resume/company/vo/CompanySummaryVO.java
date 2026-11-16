package com.team.resume.company.vo;

import java.time.LocalDateTime;

import lombok.Data;

/** 企业列表项：只含卡片展示所需字段。 */
@Data
public class CompanySummaryVO {

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

    /** SEED / ADMIN_IMPORT / MANUAL */
    private String source;

    /** VERIFIED / PENDING / EXPIRED */
    private String verificationStatus;

    private LocalDateTime lastVerifiedAt;
}
