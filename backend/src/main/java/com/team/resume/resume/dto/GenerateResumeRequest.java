package com.team.resume.resume.dto;

import lombok.Data;

/**
 * AI 生成新版本：templateId 必填；targetJobId 本期只透传存储，JD 适配在 D10。
 */
@Data
public class GenerateResumeRequest {

    private String templateId;

    private Long targetJobId;

    private String generationReason;
}
