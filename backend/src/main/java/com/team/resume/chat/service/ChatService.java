package com.team.resume.chat.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.Message;
import com.team.resume.chat.dto.CreateSessionRequest;
import com.team.resume.chat.dto.SendMessageRequest;
import com.team.resume.chat.entity.ChatMessage;
import com.team.resume.chat.entity.ChatSession;
import com.team.resume.chat.mapper.ChatMessageMapper;
import com.team.resume.chat.mapper.ChatSessionMapper;
import com.team.resume.chat.vo.ChatMessageVO;
import com.team.resume.chat.vo.ChatSessionVO;
import com.team.resume.chat.vo.SendMessageResponse;
import com.team.resume.common.BusinessException;

/**
 * 对话建档业务（切片 2）：
 * - 会话/消息按 user_id 严格隔离
 * - AI 失败时用户消息与助手回复都不落库（docs/api.md §5）
 * - D3 只返回抽取候选信息；证据落库与确认在 D4 实现
 */
@Service
public class ChatService {

    private static final String DEFAULT_TITLE = "新的求职对话";
    private static final int HISTORY_LIMIT = 20;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AIService aiService;

    public ChatService(ChatSessionMapper sessionMapper,
                       ChatMessageMapper messageMapper,
                       AIService aiService) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.aiService = aiService;
    }

    public ChatSessionVO createSession(Long userId, CreateSessionRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        String requestedTitle = request == null ? null : request.getTitle();
        session.setTitle(requestedTitle == null || requestedTitle.isBlank()
                ? DEFAULT_TITLE
                : requestedTitle.trim());
        session.setStatus("ACTIVE");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    public List<ChatSessionVO> listSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getUpdatedAt)
                        .orderByDesc(ChatSession::getId))
                .stream()
                .map(ChatService::toSessionVO)
                .toList();
    }

    public List<ChatMessageVO> listMessages(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId))
                .stream()
                .map(m -> new ChatMessageVO(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    /**
     * 发送一条消息：加载最近上下文 → 调用 AI → 成功后才保存用户消息与助手回复。
     */
    @Transactional
    public SendMessageResponse send(Long userId, SendMessageRequest request) {
        ChatSession session = requireOwnedSession(userId, request.getSessionId());

        List<ChatMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + HISTORY_LIMIT));
        List<ChatMessage> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);

        String userContent = request.getMessage().trim();
        List<Message> history = chronological.stream()
                .map(m -> new Message(
                        ChatMessage.ROLE_USER.equals(m.getRole()) ? "user" : "assistant",
                        m.getContent()))
                .toList();

        ChatExtractionResult result = aiService.chat(history, userContent);

        LocalDateTime now = LocalDateTime.now();
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(session.getId());
        userMessage.setRole(ChatMessage.ROLE_USER);
        userMessage.setContent(userContent);
        userMessage.setCreatedAt(now);
        messageMapper.insert(userMessage);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(session.getId());
        assistantMessage.setRole(ChatMessage.ROLE_ASSISTANT);
        assistantMessage.setContent(result.reply());
        assistantMessage.setCreatedAt(now);
        messageMapper.insert(assistantMessage);

        if (chronological.isEmpty() && DEFAULT_TITLE.equals(session.getTitle())) {
            session.setTitle(truncate(userContent, 30));
        }
        session.setUpdatedAt(now);
        sessionMapper.updateById(session);

        return new SendMessageResponse(session.getId(), result.reply(),
                result.extractedInfo(), result.missingFields());
    }

    private ChatSession requireOwnedSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return session;
    }

    private static ChatSessionVO toSessionVO(ChatSession session) {
        return new ChatSessionVO(session.getId(), session.getTitle(), session.getStatus(),
                session.getCreatedAt(), session.getUpdatedAt());
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
