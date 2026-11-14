package com.team.resume.resume.draft.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 全量自动保存：必须携带读取时拿到的 revision，服务端按乐观锁更新。
 */
public record SaveDraftRequest(Integer currentStep, Integer revision, JsonNode content, String draftName) {
}
