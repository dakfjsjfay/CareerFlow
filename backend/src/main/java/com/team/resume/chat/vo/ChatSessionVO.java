package com.team.resume.chat.vo;

import java.time.LocalDateTime;

/**
 * 会话列表项。
 */
public record ChatSessionVO(Long id, String title, String status,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
}
