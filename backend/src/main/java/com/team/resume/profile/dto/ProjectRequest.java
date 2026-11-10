package com.team.resume.profile.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 项目经历新增/修改（docs/api.md §6）。
 */
@Data
public class ProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 200, message = "项目名称不能超过 200 个字符")
    private String projectName;

    @Size(max = 100, message = "角色不能超过 100 个字符")
    private String role;

    private String description;

    private List<@Size(max = 100, message = "单个技术不能超过 100 个字符") String> techStack;

    private LocalDate startDate;

    private LocalDate endDate;
}
