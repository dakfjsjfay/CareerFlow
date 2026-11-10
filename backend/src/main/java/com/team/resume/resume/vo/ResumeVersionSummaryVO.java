package com.team.resume.resume.vo;

import java.time.LocalDateTime;

/**
 * 版本列表项（不含 content，避免列表响应过大）。
 */
public record ResumeVersionSummaryVO(
        Long versionId,
        Integer versionNumber,
        String generationReason,
        String templateId,
        LocalDateTime createdAt) {
}
