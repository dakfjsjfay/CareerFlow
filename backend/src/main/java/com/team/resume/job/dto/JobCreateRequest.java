package com.team.resume.job.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 手动新增岗位（docs/api.md §8，MVP 仅开发用途）。
 * requirements 可省略；省略后可通过 POST /api/jobs/{id}/parse 由 AI 解析 JD 生成。
 */
@Data
public class JobCreateRequest {

    @NotBlank(message = "公司不能为空")
    @Size(max = 200, message = "公司不能超过200个字符")
    private String company;

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 200, message = "岗位名称不能超过200个字符")
    private String position;

    @Size(max = 100, message = "城市不能超过100个字符")
    private String city;

    @Size(max = 100, message = "薪资不能超过100个字符")
    private String salary;

    @Size(max = 20000, message = "JD 内容不能超过20000个字符")
    private String jdContent;

    @Size(max = 500, message = "外部链接不能超过500个字符")
    private String externalUrl;

    @Valid
    private List<JobRequirementRequest> requirements;
}
