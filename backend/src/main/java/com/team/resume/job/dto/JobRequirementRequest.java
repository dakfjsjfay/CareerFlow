package com.team.resume.job.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class JobRequirementRequest {

    @NotBlank(message = "技能名不能为空")
    @Size(max = 100, message = "技能名不能超过100个字符")
    private String skill;

    @Pattern(regexp = "(?i)REQUIRED|PREFERRED", message = "importance 只能是 REQUIRED/PREFERRED")
    private String importance;

    @DecimalMin(value = "0.0", message = "权重不能为负数")
    @Digits(integer = 4, fraction = 2, message = "权重格式不正确")
    private BigDecimal weight;
}
