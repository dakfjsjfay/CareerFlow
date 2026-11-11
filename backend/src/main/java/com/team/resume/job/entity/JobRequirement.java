package com.team.resume.job.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 岗位技能要求表实体（job_requirement）。
 * 由 JD 解析生成，可人工修正；匹配评分以此表为基准。
 */
@Data
@TableName("job_requirement")
public class JobRequirement {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private String skill;

    /** REQUIRED（必备）/ PREFERRED（加分） */
    private String importance;

    /** 权重（用于匹配加权评分，DECIMAL(6,2)） */
    private BigDecimal weight;
}
