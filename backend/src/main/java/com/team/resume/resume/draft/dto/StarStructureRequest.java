package com.team.resume.resume.draft.dto;

/**
 * STAR 结构化请求：四个要素都来自用户填写，AI 只做专业表达润色。
 */
public record StarStructureRequest(String situation, String task, String action, String result) {
}
