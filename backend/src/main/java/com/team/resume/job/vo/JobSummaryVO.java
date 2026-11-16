package com.team.resume.job.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 岗位列表项：不含 JD 原文（列表页只展示卡片信息）。
 * topSkills 取必备技能的前 4 个，用于卡片标签展示。
 */
@Data
public class JobSummaryVO {

    private Long id;

    private String company;

    /** 关联企业库 id；存量数据可能为空 */
    private Long companyId;

    private String position;

    private String city;

    private String salary;

    private String sourceType;

    /** 来源名称，如「企业官网」 */
    private String sourceName;

    /** 原招聘信息发布时间 */
    private LocalDateTime publishedAt;

    /** 最近核验时间 */
    private LocalDateTime verifiedAt;

    /** 有效期截止 */
    private LocalDateTime validUntil;

    /** 统一展示状态（DEMO/UNVERIFIED/VERIFIED/EXPIRED/CLOSED），前端只认该字段 */
    private String displayStatus;

    private String recruitmentBatch;

    private String recruitmentStatus;

    private Integer requirementCount;

    private List<String> topSkills;
}
