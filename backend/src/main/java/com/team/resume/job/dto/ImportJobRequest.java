package com.team.resume.job.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 导入外部岗位请求（用户粘贴招聘链接或 JD 文本）：
 * 创建者为本人，岗位私有、来源 MANUAL、可信度 UNVERIFIED。
 */
@Data
public class ImportJobRequest {

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

    @Size(max = 100, message = "来源名称过长")
    private String sourceName;

    private LocalDateTime publishedAt;

    /** 是否立即用 AI 解析 JD 生成技能要求 */
    private boolean parseJd;
}
