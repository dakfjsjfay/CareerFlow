package com.team.resume.resume.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.SectionRewriteResult;
import com.team.resume.ai.dto.StarStructureResult;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.draft.dto.SectionRewriteRequest;
import com.team.resume.resume.draft.dto.StarStructureRequest;
import com.team.resume.resume.draft.entity.ResumeDraft;
import com.team.resume.resume.draft.service.DraftContentConverter;
import com.team.resume.resume.draft.service.ResumeDraftService;
import com.team.resume.resume.edit.dto.ConfirmEditRequest;
import com.team.resume.resume.edit.entity.ResumeEditHistory;
import com.team.resume.resume.edit.mapper.ResumeEditHistoryMapper;
import com.team.resume.resume.edit.service.CandidateFactGuard;
import com.team.resume.resume.edit.service.ResumeEditHistoryService;
import com.team.resume.resume.edit.vo.ResumeEditHistoryVO;
import com.team.resume.resume.mapper.ResumeMapper;

/**
 * 候选修改流程单测：AI 候选只进 PENDING、确认才写草稿、事实校验兜底。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeEditHistoryServiceTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private ResumeEditHistoryMapper historyMapper;
    @Mock
    private ResumeDraftService draftService;
    @Mock
    private AIService aiService;
    @Mock
    private ResumeMapper resumeMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeEditHistoryService service;
    private ResumeDraft draft;

    @BeforeEach
    void setUp() {
        DraftContentConverter converter = new DraftContentConverter(objectMapper);
        CandidateFactGuard factGuard = new CandidateFactGuard(objectMapper);
        service = new ResumeEditHistoryService(historyMapper, draftService, converter, aiService,
                factGuard, resumeMapper, objectMapper);
        draft = new ResumeDraft();
        draft.setId(11L);
        draft.setUserId(USER_ID);
        draft.setStatus("DRAFT");
        draft.setRevision(2);
        draft.setContent(content());
        when(draftService.requireOwned(USER_ID, 11L)).thenReturn(draft);
    }

    @Test
    void rewriteFallsBackToOriginalCompanyAndStripsFabricatedNumbers() {
        ArrayNode after = objectMapper.createArrayNode();
        ObjectNode item = after.addObject();
        item.put("id", "e1");
        item.put("company", "用户没提过的公司");
        item.put("position", "后端开发");
        item.put("description", "负责订单系统。接口 QPS 提升 300%。");

        when(aiService.rewriteSection(eq("EXPERIENCE"), anyString(), anyString(), anyString()))
                .thenReturn(new SectionRewriteResult(after, List.of("AI 已优化表达")));
        doAnswer(invocation -> {
            ResumeEditHistory history = invocation.getArgument(0);
            history.setId(501L);
            return 1;
        }).when(historyMapper).insert(any(ResumeEditHistory.class));

        ResumeEditHistoryVO vo = service.rewrite(USER_ID, 11L, "EXPERIENCE",
                new SectionRewriteRequest("更专业", null));

        assertEquals(501L, vo.id());
        assertEquals("PENDING", vo.status());
        JsonNode stored = vo.afterContent().get(0);
        assertEquals("旧公司", stored.path("company").asText(), "未提供的事实必须回退原文");
        assertFalse(stored.path("description").asText().contains("300%"), "不得保留编造的数字");
        assertTrue(vo.warnings().stream().anyMatch(warning -> warning.contains("已回退")),
                "必须把回退原因告知用户");
        assertEquals("PENDING", vo.status());
        verify(draftService, never()).applySection(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void confirmAppliesCandidateToDraft() {
        ResumeEditHistory history = pendingHistory(601L, "EXPERIENCE");
        when(historyMapper.selectById(601L)).thenReturn(history);

        ResumeEditHistoryVO vo = service.confirm(USER_ID, 601L, null);

        verify(draftService).applySection(eq(USER_ID), eq(11L), eq("EXPERIENCE"), any(JsonNode.class));
        assertEquals("APPLIED", vo.status());
        assertNotNull(vo.appliedAt());
    }

    @Test
    void confirmStarCandidateReplacesSingleItem() {
        ResumeEditHistory history = pendingHistory(602L, "EXPERIENCE");
        history.setPromptSummary("STAR:e1");
        when(historyMapper.selectById(602L)).thenReturn(history);

        service.confirm(USER_ID, 602L, new ConfirmEditRequest(
                objectMapper.createObjectNode().put("id", "e1")));

        verify(draftService).applyItem(eq(USER_ID), eq(11L), eq("EXPERIENCE"), any(JsonNode.class));
    }

    @Test
    void confirmTwiceReturns409() {
        ResumeEditHistory history = pendingHistory(603L, "EXPERIENCE");
        history.setStatus("APPLIED");
        when(historyMapper.selectById(603L)).thenReturn(history);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.confirm(USER_ID, 603L, null));

        assertEquals(409, error.getCode());
        verify(draftService, never()).applySection(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void rejectKeepsDraftUntouched() {
        ResumeEditHistory history = pendingHistory(604L, "PROJECT");
        when(historyMapper.selectById(604L)).thenReturn(history);

        ResumeEditHistoryVO vo = service.reject(USER_ID, 604L);

        assertEquals("REJECTED", vo.status());
        verify(draftService, never()).applySection(anyLong(), anyLong(), anyString(), any());
        verify(draftService, never()).applyItem(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void othersHistoryReturns403() {
        ResumeEditHistory history = pendingHistory(605L, "BASIC");
        history.setUserId(2002L);
        when(historyMapper.selectById(605L)).thenReturn(history);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.detail(USER_ID, 605L));

        assertEquals(403, error.getCode());
    }

    @Test
    void structureStarStoresPendingCandidate() {
        ObjectNode star = objectMapper.createObjectNode()
                .put("situation", "订单系统流量增长")
                .put("task", "负责性能优化")
                .put("action", "引入缓存")
                .put("result", "响应时间下降 40%");
        when(aiService.structureStar(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StarStructureResult(star, "负责订单系统性能优化", List.of()));
        doAnswer(invocation -> {
            ResumeEditHistory history = invocation.getArgument(0);
            history.setId(701L);
            return 1;
        }).when(historyMapper).insert(any(ResumeEditHistory.class));

        ResumeEditHistoryVO vo = service.structureStar(USER_ID, 11L, "e1",
                new StarStructureRequest("订单系统流量增长", "负责性能优化", "引入缓存", "响应时间下降 40%"));

        assertEquals("PENDING", vo.status());
        assertEquals("STAR:e1", vo.promptSummary());
        // 40% 来自用户输入，可以保留
        assertTrue(vo.afterContent().path("star").path("result").asText().contains("40%"));
        ArgumentCaptor<ResumeEditHistory> captor = ArgumentCaptor.forClass(ResumeEditHistory.class);
        verify(historyMapper).insert(captor.capture());
        assertEquals(11L, captor.getValue().getDraftId());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    @Test
    void structureStarRejectsEmptyInput() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.structureStar(USER_ID, 11L, "e1",
                        new StarStructureRequest("", "", "", "")));
        assertEquals(400, error.getCode());
    }

    private ResumeEditHistory pendingHistory(Long id, String sectionKey) {
        ResumeEditHistory history = new ResumeEditHistory();
        history.setId(id);
        history.setUserId(USER_ID);
        history.setDraftId(11L);
        history.setSectionKey(sectionKey);
        history.setStatus("PENDING");
        history.setSource("AI");
        history.setBeforeContent(objectMapper.createArrayNode());
        history.setAfterContent(objectMapper.createArrayNode().addObject().put("id", "e1"));
        history.setWarnings(List.of());
        history.setCreatedAt(LocalDateTime.now());
        return history;
    }

    private ObjectNode content() {
        ObjectNode content = objectMapper.createObjectNode();
        content.putObject("basic").put("realName", "张三");
        ObjectNode experience = content.putArray("experiences").addObject();
        experience.put("id", "e1");
        experience.put("company", "旧公司");
        experience.put("position", "后端开发");
        experience.put("description", "负责订单系统");
        ObjectNode star = experience.putObject("star");
        star.put("situation", "");
        star.put("task", "");
        star.put("action", "");
        star.put("result", "");
        content.putArray("projects");
        content.putArray("educations");
        content.putArray("skills");
        content.putObject("soft");
        return content;
    }
}
