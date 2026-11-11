package com.team.resume.job.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 岗位技能要求（job_requirement）：由 JD 解析或手动录入，权重由 Java 归一化为合计 100。
 */
@Data
@TableName("job_requirement")
public class JobRequirement {

    public static final String IMPORTANCE_REQUIRED = "REQUIRED";
    public static final String IMPORTANCE_PREFERRED = "PREFERRED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private String skill;

    private String importance;

    private BigDecimal weight;
}
