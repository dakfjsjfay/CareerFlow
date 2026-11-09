package com.team.resume.ai.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DeepSeek Chat Completions 请求体。
 * response_format 声明 JSON 输出，降低模型返回 Markdown 代码块的概率。
 * thinking.type=disabled 可关闭推理阶段，显著降低响应延迟。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(String model, List<Message> messages,
                                   double temperature, Map<String, String> response_format,
                                   Integer max_tokens, Map<String, String> thinking) {
}
