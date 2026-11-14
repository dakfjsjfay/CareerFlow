package com.team.resume.resume.draft.vo;

/**
 * 完成草稿后的结果：同时返回简历 ID 与新建版本信息。
 */
public record DraftCompleteVO(Long resumeId, Long versionId, Integer versionNumber, Long draftId) {
}
