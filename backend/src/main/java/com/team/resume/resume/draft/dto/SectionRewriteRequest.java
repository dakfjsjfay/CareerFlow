package com.team.resume.resume.draft.dto;

/**
 * 生成候选修改：只写候选区（resume_edit_history），不直接改草稿。
 */
public record SectionRewriteRequest(String instruction, Long jobId) {
}
