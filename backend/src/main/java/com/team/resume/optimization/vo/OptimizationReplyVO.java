package com.team.resume.optimization.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 回复 AI 追问的响应（docs/api.md §10）。
 * extractedInfo 供前端复用待确认证据组件，为契约基础上的附加字段。
 */
@Data
@AllArgsConstructor
public class OptimizationReplyVO {

    private String reply;

    private boolean nextQuestion;

    private String taskStatus;

    private JsonNode extractedInfo;

    private List<String> missingFields;
}
