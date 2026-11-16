package com.team.resume.job.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 岗位核验请求（仅管理员，docs/api.md §8）：
 * status=VERIFIED/UNVERIFIED/EXPIRED，可在同一次请求补充来源信息。
 */
@Data
public class VerifyJobRequest {

    @NotBlank(message = "核验状态不能为空")
    private String verificationStatus;

    @Size(max = 100, message = "来源名称过长")
    private String sourceName;

    @Size(max = 500, message = "链接过长")
    private String externalUrl;

    private LocalDateTime publishedAt;

    private LocalDateTime validUntil;
}
