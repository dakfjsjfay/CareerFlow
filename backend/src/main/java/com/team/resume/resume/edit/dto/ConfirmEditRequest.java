package com.team.resume.resume.edit.dto;

/**
 * 确认候选修改：finalContent 为用户二次编辑后的最终内容，可为空表示直接用 AI 候选。
 */
public record ConfirmEditRequest(Object finalContent) {
}
