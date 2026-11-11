package com.team.resume.job.vo;

import java.util.List;

import lombok.Data;

/**
 * 岗位详情：含 JD 原文与技能要求列表（docs/api.md §8）。
 */
@Data
public class JobDetailVO {

    private Long id;

    private String company;

    private String position;

    private String city;

    private String salary;

    private String jdContent;

    private String sourceType;

    private String externalUrl;

    private List<JobRequirementVO> requirements;
}
