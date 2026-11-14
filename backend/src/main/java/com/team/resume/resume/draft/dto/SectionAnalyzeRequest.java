package com.team.resume.resume.draft.dto;

/**
 * 单区块 AI 分析请求：focus 限定关注点，jobId 用于岗位定向。
 */
public record SectionAnalyzeRequest(String focus, Long jobId) {
}
