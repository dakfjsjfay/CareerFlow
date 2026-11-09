package com.team.resume.ai.dto;

/**
 * OpenAI / DeepSeek 兼容协议中的一条消息。
 */
public record Message(String role, String content) {
}
