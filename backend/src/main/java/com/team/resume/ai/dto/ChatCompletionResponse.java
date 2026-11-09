package com.team.resume.ai.dto;

import java.util.List;

/**
 * DeepSeek Chat Completions 响应体（只取需要的字段）。
 */
public record ChatCompletionResponse(List<Choice> choices, Usage usage) {

    public record Choice(Message message, String finish_reason) {
    }

    public record Usage(Integer completion_tokens, Integer total_tokens) {
    }
}
