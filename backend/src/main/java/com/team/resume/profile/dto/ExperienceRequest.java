package com.team.resume.profile.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 工作/实习经历新增/修改（docs/api.md §6）。
 */
@Data
public class ExperienceRequest {

    @NotBlank(message = "公司/单位不能为空")
    @Size(max = 200, message = "公司/单位不能超过 200 个字符")
    private String company;

    @Size(max = 100, message = "职位不能超过 100 个字符")
    private String position;

    private String description;

    private String achievements;

    private LocalDate startDate;

    private LocalDate endDate;
}
