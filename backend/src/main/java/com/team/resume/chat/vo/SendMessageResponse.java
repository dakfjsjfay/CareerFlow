package com.team.resume.chat.vo;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 发送消息返回（docs/api.md §5）：
 * { sessionId, reply, extractedInfo, missingFields }
 * <p>
 * D3 只返回抽取结果；D4 会在此基础上把 extractedInfo 落为 PENDING 证据。
 */
public record SendMessageResponse(Long sessionId, String reply,
                                  JsonNode extractedInfo, List<String> missingFields) {
}
