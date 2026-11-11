package com.team.resume.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 手动新增岗位请求（docs/api.md §8）。
 */
@Data
public class CreateJobRequest {

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 200, message = "岗位名称过长")
    private String position;

    @Size(max = 200, message = "公司名称过长")
    private String company;

    @Size(max = 100, message = "城市名称过长")
    private String city;

    @Size(max = 100, message = "薪资描述过长")
    private String salary;

    private String jdContent;

    @Size(max = 500, message = "链接过长")
    private String externalUrl;
}
