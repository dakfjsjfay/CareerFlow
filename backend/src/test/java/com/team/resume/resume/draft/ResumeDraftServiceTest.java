package com.team.resume.resume.draft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.draft.dto.CompleteDraftRequest;
import com.team.resume.resume.draft.dto.CreateDraftRequest;
import com.team.resume.resume.draft.dto.SaveDraftRequest;
import com.team.resume.resume.draft.entity.ResumeDraft;
import com.team.resume.resume.draft.mapper.ResumeDraftMapper;
import com.team.resume.resume.draft.service.DraftContentConverter;
import com.team.resume.resume.draft.service.ResumeDraftService;
import com.team.resume.resume.draft.service.StarFollowUpAdvisor;
import com.team.resume.resume.draft.vo.DraftCompleteVO;
import com.team.resume.resume.draft.vo.ResumeDraftVO;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.service.ResumeVersionService;

/**
 * 八步草稿服务单测：覆盖创建、乐观锁冲突、越权、完成生成版本。
 */
@ExtendWith(MockitoExtension.class)
class ResumeDraftServiceTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private ResumeDraftMapper draftMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private ResumeVersionService versionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ResumeDraftService service;

    @BeforeEach
    void setUp() {
        DraftContentConverter converter = new DraftContentConverter(objectMapper);
        StarFollowUpAdvisor advisor = new StarFollowUpAdvisor(objectMapper);
        service = new ResumeDraftService(draftMapper, resumeMapper, versionService, converter, advisor, objectMapper);
    }

    @Test
    void createStartsAtFirstStepWithRevisionOne() {
        doAnswer(invocation -> {
            ResumeDraft draft = invocation.getArgument(0);
            draft.setId(9L);
            return 1;
        }).when(draftMapper).insert(any(ResumeDraft.class));

        ResumeDraftVO vo = service.create(USER_ID, new CreateDraftRequest(null, "2027 届简历", null));

        assertEquals(9L, vo.id());
        assertEquals(1, vo.currentStep());
        assertEquals(1, vo.revision());
        assertEquals(8, vo.totalSteps());
        assertEquals("DRAFT", vo.status());
        assertNotNull(vo.content());
        assertTrue(vo.content().isObject());
    }

    @Test
    void saveBumpsRevisionOnSuccess() {
        when(draftMapper.updateContent(eq(5L), eq(USER_ID), eq(1), eq(2), anyString(), any()))
                .thenReturn(1);
        ResumeDraft persisted = draft(5L, USER_ID, "DRAFT", 2, content("张三", "13800000000"));
        persisted.setCurrentStep(2);
        when(draftMapper.selectById(5L)).thenReturn(persisted);

        ResumeDraftVO vo = service.save(USER_ID, 5L,
                new SaveDraftRequest(2, 1, content("张三", "13800000000"), null));

        assertEquals(2, vo.revision());
        assertEquals(2, vo.currentStep());
    }

    @Test
    void staleRevisionReturns409() {
        when(draftMapper.updateContent(anyLong(), anyLong(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(0);
        when(draftMapper.selectById(5L)).thenReturn(draft(5L, USER_ID, "DRAFT", 3,
                content("张三", "13800000000")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(USER_ID, 5L,
                        new SaveDraftRequest(2, 1, content("张三", "13800000000"), null)));

        assertEquals(409, error.getCode());
        assertTrue(error.getMessage().contains("刷新"));
    }

    @Test
    void saveOthersDraftReturns403() {
        when(draftMapper.updateContent(anyLong(), anyLong(), anyInt(), anyInt(), anyString(), any()))
                .thenReturn(0);
        when(draftMapper.selectById(5L)).thenReturn(draft(5L, 2002L, "DRAFT", 1,
                content("李四", "13900000000")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.save(USER_ID, 5L,
                        new SaveDraftRequest(1, 1, content("张三", "13800000000"), null)));

        assertEquals(403, error.getCode());
    }

    @Test
    void completeCreatesImmutableVersionWithSourceTrace() {
        ResumeDraft draft = draft(7L, USER_ID, "DRAFT", 4, content("张三", "13800000000"));
        when(draftMapper.selectById(7L)).thenReturn(draft);
        doAnswer(invocation -> {
            Resume resume = invocation.getArgument(0);
            resume.setId(21L);
            return 1;
        }).when(resumeMapper).insert(any(Resume.class));

        ResumeVersion version = new ResumeVersion();
        version.setId(33L);
        version.setVersionNumber(1);
        when(versionService.createRaw(eq(USER_ID), eq(21L), any(JsonNode.class), eq("INITIAL")))
                .thenReturn(version);
        when(draftMapper.updateStatus(7L, USER_ID, "COMPLETED", 21L, 8)).thenReturn(1);

        DraftCompleteVO result = service.complete(USER_ID, 7L,
                new CompleteDraftRequest("Java 后端简历", "tpl-1", null));

        assertEquals(21L, result.resumeId());
        assertEquals(33L, result.versionId());
        assertEquals(1, result.versionNumber());
        assertEquals(7L, result.draftId());

        ArgumentCaptor<JsonNode> contentCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(versionService).createRaw(eq(USER_ID), eq(21L), contentCaptor.capture(), eq("INITIAL"));
        JsonNode versionContent = contentCaptor.getValue();
        assertEquals("张三", versionContent.path("personalInfo").path("realName").asText());
        assertTrue(versionContent.has("sourceTrace"), "版本内容必须携带来源追溯链");
        assertEquals("tpl-1", versionContent.path("meta").path("templateId").asText());
        assertTrue(versionContent.path("meta").path("generatedAt").asText().length() > 0);
    }

    @Test
    void completeTwiceReturns409() {
        when(draftMapper.selectById(7L)).thenReturn(draft(7L, USER_ID, "COMPLETED", 8,
                content("张三", "13800000000")));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.complete(USER_ID, 7L, new CompleteDraftRequest("简历", "tpl", null)));

        assertEquals(409, error.getCode());
        verify(versionService, never()).createRaw(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void completeWithoutNameReturns400() {
        ObjectNode content = content("", "13800000000");
        when(draftMapper.selectById(7L)).thenReturn(draft(7L, USER_ID, "DRAFT", 1, content));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.complete(USER_ID, 7L, new CompleteDraftRequest("简历", "tpl", null)));

        assertEquals(400, error.getCode());
    }

    private ObjectNode content(String name, String phone) {
        ObjectNode content = objectMapper.createObjectNode();
        ObjectNode basic = content.putObject("basic");
        basic.put("realName", name);
        basic.put("phone", phone);
        basic.put("email", "");
        content.putObject("preview").put("templateId", "tpl-1");
        content.putArray("educations");
        content.putArray("experiences");
        content.putArray("projects");
        content.putArray("skills");
        content.putObject("soft");
        content.putObject("photo");
        return content;
    }

    private ResumeDraft draft(Long id, Long userId, String status, int revision, ObjectNode content) {
        ResumeDraft draft = new ResumeDraft();
        draft.setId(id);
        draft.setUserId(userId);
        draft.setStatus(status);
        draft.setRevision(revision);
        draft.setCurrentStep(1);
        draft.setContent(content);
        draft.setLastSavedAt(LocalDateTime.now());
        return draft;
    }
}
