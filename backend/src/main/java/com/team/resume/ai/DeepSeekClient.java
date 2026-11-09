package com.team.resume.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.team.resume.ai.dto.ChatCompletionRequest;
import com.team.resume.ai.dto.ChatCompletionResponse;
import com.team.resume.ai.dto.Message;
import com.team.resume.common.BusinessException;

/**
 * DeepSeek API 唯一调用入口（架构文档 §4）。
 * <p>
 * 职责边界：只负责发 HTTP 和取 content，不做业务校验；
 * API Key 只从环境变量读取，禁止打印到日志。
 */
@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);
    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public DeepSeekClient(@Value("${ai.deepseek.base-url}") String baseUrl,
                          @Value("${ai.deepseek.api-key}") String apiKey,
                          @Value("${ai.deepseek.model}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        String normalizedBaseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(60));

        this.restClient = RestClient.builder()
                .baseUrl(normalizedBaseUrl)
                .requestFactory(factory)
                .build();
    }

    /**
     * 调用 DeepSeek 并返回模型生成的 JSON 字符串。
     * 失败重试 1 次，仍失败则抛 502（调用方不落库）。
     */
    public String completeJson(List<Message> messages) {
        ensureConfigured();
        ChatCompletionRequest request = new ChatCompletionRequest(
                model, messages, 0.3, Map.of("type", "json_object"));

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ChatCompletionResponse response = restClient.post()
                        .uri("/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(ChatCompletionResponse.class);
                return extractContent(response);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 401) {
                    throw new BusinessException(502,
                            "AI 服务鉴权失败，请检查后端环境变量 DEEPSEEK_API_KEY 是否有效");
                }
                if (status == 404) {
                    throw new BusinessException(502,
                            "AI 模型不可用，请检查 DEEPSEEK_MODEL 与 DEEPSEEK_BASE_URL 配置");
                }
                log.warn("DeepSeek 调用失败（第 {} 次）：HTTP {}", attempt, status);
            } catch (ResourceAccessException e) {
                log.warn("DeepSeek 连接失败（第 {} 次）：{}", attempt, e.getMessage());
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("DeepSeek 调用异常（第 {} 次）：{}", attempt, e.getClass().getSimpleName());
            }
            sleepBeforeRetry(attempt);
        }
        throw new BusinessException(502, "AI 服务调用失败，请稍后重试");
    }

    private void ensureConfigured() {
        if (apiKey.isEmpty() || apiKey.contains("请填入")) {
            throw new BusinessException(502, "AI 服务未配置，请在后端环境变量中设置 DEEPSEEK_API_KEY");
        }
    }

    private static String extractContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new BusinessException(502, "AI 返回内容为空，请稍后重试");
        }
        Message message = response.choices().get(0).message();
        if (message == null || message.content() == null || message.content().isBlank()) {
            throw new BusinessException(502, "AI 返回内容为空，请稍后重试");
        }
        return message.content();
    }

    private static void sleepBeforeRetry(int attempt) {
        if (attempt >= MAX_ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
