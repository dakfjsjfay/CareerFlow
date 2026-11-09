package com.team.resume.chat.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team.resume.chat.dto.CreateSessionRequest;
import com.team.resume.chat.dto.SendMessageRequest;
import com.team.resume.chat.service.ChatService;
import com.team.resume.chat.vo.ChatMessageVO;
import com.team.resume.chat.vo.ChatSessionVO;
import com.team.resume.chat.vo.SendMessageResponse;
import com.team.resume.common.Result;

import jakarta.validation.Valid;

/**
 * 对话建档接口（docs/api.md §5）：
 * POST /api/chat/sessions               创建会话
 * GET  /api/chat/sessions               会话列表
 * GET  /api/chat/sessions/{id}/messages 历史消息（归属校验）
 * POST /api/chat/send                   发送消息（后端组织多轮上下文）
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody(required = false) CreateSessionRequest request) {
        return Result.ok(chatService.createSession(userId, request));
    }

    @GetMapping("/sessions")
    public Result<List<ChatSessionVO>> listSessions(@AuthenticationPrincipal Long userId) {
        return Result.ok(chatService.listSessions(userId));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessageVO>> listMessages(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long sessionId) {
        return Result.ok(chatService.listMessages(userId, sessionId));
    }

    @PostMapping("/send")
    public Result<SendMessageResponse> send(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody SendMessageRequest request) {
        return Result.ok(chatService.send(userId, request));
    }
}
