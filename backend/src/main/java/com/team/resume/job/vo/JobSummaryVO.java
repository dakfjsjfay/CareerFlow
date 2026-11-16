package com.team.resume.job.vo;

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

    private String recruitmentBatch;

    private String recruitmentStatus;

    private Integer requirementCount;

    private List<String> topSkills;
}
