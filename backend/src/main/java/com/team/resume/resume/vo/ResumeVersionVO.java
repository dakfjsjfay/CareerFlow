package com.team.resume.resume.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 指定版本的完整内容。
 */
public record ResumeVersionVO(
        Long versionId,
        Integer versionNumber,
        String generationReason,
        String templateId,
        JsonNode content,
        LocalDateTime createdAt) {
}
