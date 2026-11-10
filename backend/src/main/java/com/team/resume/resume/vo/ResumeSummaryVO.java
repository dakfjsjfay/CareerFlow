package com.team.resume.resume.vo;

import java.time.LocalDateTime;

/**
 * 简历列表项：currentVersionNumber / templateId 来自当前版本。
 */
public record ResumeSummaryVO(
        Long id,
        String resumeName,
        Long currentVersionId,
        Integer currentVersionNumber,
        String templateId,
        LocalDateTime updatedAt) {
}
