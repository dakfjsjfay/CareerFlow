package com.team.resume.chat.vo;

import java.time.LocalDateTime;

/**
 * 历史消息（role 为 USER / ASSISTANT）。
 */
public record ChatMessageVO(Long id, String role, String content, LocalDateTime createdAt) {
}
