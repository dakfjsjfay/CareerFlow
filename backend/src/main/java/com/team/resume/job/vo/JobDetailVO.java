package com.team.resume.job.vo;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 岗位详情：含 JD 原文与技能要求列表（docs/api.md §8）。
 */
@Data
public class JobDetailVO {

    private Long id;

    private String company;

    /** 关联企业库 id；存量数据可能为空 */
    private Long companyId;

    private String position;

    private String city;

    private String salary;

    private String jdContent;

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

    private String externalUrl;

    /** 当前用户是否可修改该岗位（ADMIN 或岗位创建者），前端据此展示解析按钮 */
    private Boolean canEdit;

    private List<JobRequirementVO> requirements;
}
