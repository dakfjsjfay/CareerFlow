package com.team.resume.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek Chat Completions 请求体。
 * response_format 声明 JSON 输出，降低模型返回 Markdown 代码块的概率。
 */
public record ChatCompletionRequest(String model, List<Message> messages,
                                   double temperature, Map<String, String> response_format) {
}
