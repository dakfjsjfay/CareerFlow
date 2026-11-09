package com.team.resume.profile.vo;

import java.time.LocalDateTime;

/**
 * 证据列表项（docs/api.md §6.1）。
 * sourceContent 为来源消息原文（CHAT 来源时由 source_id 关联 chat_message 得到），
 * 便于前端展示「AI 从哪句话里抽取了这条信息」。
 */
public record EvidenceVO(
        Long id,
        String entityType,
        Long entityId,
        String fieldName,
        String sourceType,
        Long sourceId,
        String sourceContent,
        String evidenceContent,
        String confirmationStatus,
        LocalDateTime createdAt) {
}
