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

    /** 当前用户是否可修改该岗位（ADMIN 或岗位创建者），前端据此展示解析按钮 */
    private Boolean canEdit;

    private List<JobRequirementVO> requirements;
}
