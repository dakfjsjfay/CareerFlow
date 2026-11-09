package com.team.resume.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.chat.dto.SendMessageRequest;
import com.team.resume.chat.entity.ChatMessage;
import com.team.resume.chat.entity.ChatSession;
import com.team.resume.chat.mapper.ChatMessageMapper;
import com.team.resume.chat.mapper.ChatSessionMapper;
import com.team.resume.chat.service.ChatService;
import com.team.resume.chat.vo.ChatSessionVO;
import com.team.resume.chat.vo.SendMessageResponse;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.service.EvidenceService;
import com.team.resume.profile.service.EvidenceSource;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private AIService aiService;

    @Mock
    private EvidenceService evidenceService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(sessionMapper, messageMapper, aiService, evidenceService);
    }

    @Test
    void createSessionUsesDefaultTitle() {
        when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(10L);
            return 1;
        });

        ChatSessionVO result = chatService.createSession(1L, null);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.title()).isEqualTo("新的求职对话");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.createdAt()).isNotNull();
    }

    @Test
    void readingAnotherUsersSessionReturns403() {
        when(sessionMapper.selectById(5L)).thenReturn(session(5L, 2L));

        assertThatThrownBy(() -> chatService.listMessages(1L, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
    }

    @Test
    void missingSessionReturns404() {
        when(sessionMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> chatService.listMessages(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    @Test
    void sendSavesUserAndAssistantMessagesAfterAiSuccess() {
        ChatSession session = session(5L, 1L);
        when(sessionMapper.selectById(5L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(messageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(ChatMessage.ROLE_USER.equals(message.getRole()) ? 201L : 202L);
            return 1;
        });
        when(aiService.chat(anyList(), eq("我用 Java 做过学生管理系统")))
                .thenReturn(new ChatExtractionResult(
                        "你负责了项目中的哪些功能？",
                        new ObjectMapper().createObjectNode(),
                        List.of("个人负责模块")));

        SendMessageResponse result = chatService.send(1L,
                sendRequest(5L, "  我用 Java 做过学生管理系统  "));

        assertThat(result.sessionId()).isEqualTo(5L);
        assertThat(result.reply()).isEqualTo("你负责了项目中的哪些功能？");
        assertThat(result.missingFields()).containsExactly("个人负责模块");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, times(2)).insert(captor.capture());
        List<ChatMessage> saved = captor.getAllValues();
        assertThat(saved).extracting(ChatMessage::getRole)
                .containsExactly(ChatMessage.ROLE_USER, ChatMessage.ROLE_ASSISTANT);
        assertThat(saved.get(0).getContent()).isEqualTo("我用 Java 做过学生管理系统");
        assertThat(saved.get(1).getContent()).isEqualTo("你负责了项目中的哪些功能？");
        verify(sessionMapper).updateById(session);
        // 抽取结果以用户消息为来源落为 PENDING 证据
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvidenceSource>> sourceCaptor = ArgumentCaptor.forClass(List.class);
        verify(evidenceService).createPendingFromExtraction(eq(1L), sourceCaptor.capture(), any());
        assertThat(sourceCaptor.getValue()).containsExactly(
                new EvidenceSource(201L, "我用 Java 做过学生管理系统"));
    }

    @Test
    void aiFailureDoesNotPersistAnyMessage() {
        when(sessionMapper.selectById(5L)).thenReturn(session(5L, 1L));
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(aiService.chat(anyList(), anyString()))
                .thenThrow(new BusinessException(502, "AI 服务调用失败"));

        assertThatThrownBy(() -> chatService.send(1L, sendRequest(5L, "你好")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);

        verify(messageMapper, never()).insert(any(ChatMessage.class));
        verify(sessionMapper, never()).updateById(any(ChatSession.class));
        verify(evidenceService, never()).createPendingFromExtraction(any(), any(), any());
    }

    @Test
    void firstMessageRenamesDefaultSession() {
        ChatSession session = session(5L, 1L);
        session.setTitle("新的求职对话");
        when(sessionMapper.selectById(5L)).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(aiService.chat(anyList(), anyString()))
                .thenReturn(new ChatExtractionResult("请继续", new ObjectMapper().createObjectNode(), List.of()));

        String longMessage = "我是计算机专业大二学生，正在学习 Java 和 MySQL 数据库";
        chatService.send(1L, sendRequest(5L, longMessage));

        assertThat(session.getTitle()).hasSize(31);
        assertThat(session.getTitle()).endsWith("…");
        assertThat(session.getUpdatedAt()).isNotNull();
    }

    @Test
    void evidenceSourcesContainOnlyUserMessagesAndEndWithCurrentMessage() {
        ChatSession session = session(5L, 1L);
        when(sessionMapper.selectById(5L)).thenReturn(session);
        ChatMessage assistantHistory = message(32L, ChatMessage.ROLE_ASSISTANT, "你好");
        ChatMessage userHistory = message(31L, ChatMessage.ROLE_USER, "我叫张三");
        when(messageMapper.selectList(any()))
                .thenReturn(new ArrayList<>(List.of(assistantHistory, userHistory)));
        when(messageMapper.insert(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            message.setId(ChatMessage.ROLE_USER.equals(message.getRole()) ? 201L : 202L);
            return 1;
        });
        when(aiService.chat(anyList(), anyString()))
                .thenReturn(new ChatExtractionResult(
                        "请继续", new ObjectMapper().createObjectNode(), List.of()));

        chatService.send(1L, sendRequest(5L, "新消息"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvidenceSource>> captor = ArgumentCaptor.forClass(List.class);
        verify(evidenceService).createPendingFromExtraction(eq(1L), captor.capture(), any());
        assertThat(captor.getValue())
                .extracting(EvidenceSource::messageId)
                .containsExactly(31L, 201L);
        assertThat(captor.getValue())
                .extracting(EvidenceSource::content)
                .containsExactly("我叫张三", "新消息");
    }

    private static ChatMessage message(Long id, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private static ChatSession session(Long id, Long userId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTitle("新的求职对话");
        session.setStatus("ACTIVE");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        return session;
    }

    private static SendMessageRequest sendRequest(Long sessionId, String message) {
        SendMessageRequest request = new SendMessageRequest();
        request.setSessionId(sessionId);
        request.setMessage(message);
        return request;
    }
}
