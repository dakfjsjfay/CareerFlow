package com.team.resume.resume.draft.dto;

import java.util.List;

/**
 * 区块 AI 对话（第一阶段无状态，历史由前端携带）。
 */
public record SectionChatRequest(String message, List<Turn> history) {

    public record Turn(String role, String content) {
    }
}
