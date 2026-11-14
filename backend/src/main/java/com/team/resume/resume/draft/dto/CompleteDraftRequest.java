package com.team.resume.resume.draft.dto;

/**
 * 第八步生成简历：templateId 必须落库到版本 meta，generationReason 默认 INITIAL。
 */
public record CompleteDraftRequest(String resumeName, String templateId, String generationReason) {
}
