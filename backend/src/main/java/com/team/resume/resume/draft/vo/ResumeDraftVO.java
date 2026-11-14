package com.team.resume.resume.draft.vo;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 草稿详情/列表项，totalSteps 固定 8，便于前端渲染步骤条。
 */
public record ResumeDraftVO(Long id,
                            Long resumeId,
                            String draftName,
                            Integer currentStep,
                            Integer totalSteps,
                            Integer revision,
                            String status,
                            LocalDateTime lastSavedAt,
                            JsonNode content) {
}
