package com.team.resume.job.vo;

import java.math.BigDecimal;

import com.team.resume.job.entity.JobRequirement;

import lombok.Data;

@Data
public class JobRequirementVO {

    private Long id;

    private String skill;

    private String importance;

    private BigDecimal weight;

    public static JobRequirementVO from(JobRequirement entity) {
        JobRequirementVO vo = new JobRequirementVO();
        vo.setId(entity.getId());
        vo.setSkill(entity.getSkill());
        vo.setImportance(entity.getImportance());
        vo.setWeight(entity.getWeight());
        return vo;
    }
}
