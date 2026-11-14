package com.team.resume.resume.draft.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 创建八步草稿：resumeId 为空表示全新简历；有值时必须属于当前用户。
 */
public record CreateDraftRequest(Long resumeId, String draftName, JsonNode content) {
}
