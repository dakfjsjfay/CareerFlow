package com.team.resume.ai.dto;

import java.util.List;

/**
 * DeepSeek Chat Completions 响应体（只取需要的字段）。
 */
public record ChatCompletionResponse(List<Choice> choices) {

    public record Choice(Message message) {
    }
}
