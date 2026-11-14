package com.team.resume.resume.edit.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.SectionAnalysisResult;
import com.team.resume.ai.dto.SectionRewriteResult;
import com.team.resume.ai.dto.StarStructureResult;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.draft.dto.SectionAnalyzeRequest;
import com.team.resume.resume.draft.dto.SectionChatRequest;
import com.team.resume.resume.draft.dto.SectionRewriteRequest;
import com.team.resume.resume.draft.dto.StarStructureRequest;
import com.team.resume.resume.draft.entity.ResumeDraft;
import com.team.resume.resume.draft.service.DraftContentConverter;
import com.team.resume.resume.draft.service.ResumeDraftService;
import com.team.resume.resume.edit.dto.ConfirmEditRequest;
import com.team.resume.resume.edit.entity.ResumeEditHistory;
import com.team.resume.resume.edit.mapper.ResumeEditHistoryMapper;
import com.team.resume.resume.edit.vo.ResumeEditHistoryVO;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.mapper.ResumeMapper;

/**
 * 区块分析与候选修改（docs/2.0/api-v2.md §4）。
 * <p>
 * 核心规则：AI 输出永不直接写草稿；必须先落 resume_edit_history(PENDING)，
 * 用户确认后才写回草稿并标记 APPLIED。所有候选在落库前经过 {@link CandidateFactGuard}。
 */
@Service
public class ResumeEditHistoryService {

    private static final String STAR_MARKER = "STAR:";
    private static final String MODEL_NAME = "deepseek-chat";

    private final ResumeEditHistoryMapper historyMapper;
    private final ResumeDraftService draftService;
    private final DraftContentConverter converter;
    private final AIService aiService;
    private final CandidateFactGuard factGuard;
    private final ResumeMapper resumeMapper;
    private final ObjectMapper objectMapper;

    public ResumeEditHistoryService(ResumeEditHistoryMapper historyMapper,
                                    ResumeDraftService draftService,
                                    DraftContentConverter converter,
                                    AIService aiService,
                                    CandidateFactGuard factGuard,
                                    ResumeMapper resumeMapper,
                                    ObjectMapper objectMapper) {
        this.historyMapper = historyMapper;
        this.draftService = draftService;
        this.converter = converter;
        this.aiService = aiService;
        this.factGuard = factGuard;
        this.resumeMapper = resumeMapper;
        this.objectMapper = objectMapper;
    }

    /** 单区块分析：不落库。 */
    public SectionAnalysisResult analyze(Long userId, Long draftId, String sectionKey,
                                         SectionAnalyzeRequest request) {
        ResumeDraft draft = draftService.requireOwned(userId, draftId);
        JsonNode section = converter.section(draft.getContent(), sectionKey);
        String jobContext = request != null && request.jobId() != null
                ? "目标岗位 ID：" + request.jobId()
                : "";
        return aiService.analyzeSection(sectionKey, section.toString(),
                request == null ? null : request.focus(), jobContext);
    }

    /** 生成候选修改：写入 PENDING，不修改草稿。 */
    public ResumeEditHistoryVO rewrite(Long userId, Long draftId, String sectionKey,
                                      SectionRewriteRequest request) {
        ResumeDraft draft = draftService.requireOwned(userId, draftId);
        JsonNode before = converter.section(draft.getContent(), sectionKey);
        String instruction = request == null ? null : request.instruction();
        String jobContext = request != null && request.jobId() != null
                ? "目标岗位 ID：" + request.jobId()
                : "";

        SectionRewriteResult aiResult = aiService.rewriteSection(
                sectionKey, before.toString(), instruction, jobContext);
        CandidateFactGuard.SanitizeResult sanitized = factGuard.sanitize(
                aiResult.afterContent(), before, before.toString() + " " + nullToEmpty(instruction));

        List<String> warnings = new ArrayList<>(aiResult.warnings());
        warnings.addAll(sanitized.warnings());
        JsonNode after = sanitized.content();
        if (isEmptyContent(after)) {
            after = before;
            warnings.add("AI 未给出可用的修改内容，已保留原文");
        }

        ResumeEditHistory history = new ResumeEditHistory();
        history.setUserId(userId);
        history.setResumeId(draft.getResumeId());
        history.setDraftId(draft.getId());
        history.setSectionKey(sectionKey);
        history.setBeforeContent(before);
        history.setAfterContent(after);
        history.setSource(ResumeEditHistory.SOURCE_AI);
        history.setStatus(ResumeEditHistory.STATUS_PENDING);
        history.setWarnings(warnings);
        history.setPromptSummary(truncate(instruction, 500));
        history.setModel(MODEL_NAME);
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
        return toVO(history);
    }

    /** 区块对话：无状态，不落库。 */
    public String chat(Long userId, Long draftId, String sectionKey, SectionChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new BusinessException(400, "请输入要咨询的内容");
        }
        ResumeDraft draft = draftService.requireOwned(userId, draftId);
        JsonNode section = converter.section(draft.getContent(), sectionKey);
        return aiService.chatSection(sectionKey, section.toString(),
                writeJson(request.history()), request.message());
    }

    /**
     * STAR 结构化：把用户填写的四要素做专业润色，生成单条经历候选（PENDING）。
     */
    public ResumeEditHistoryVO structureStar(Long userId, Long draftId, String experienceId,
                                             StarStructureRequest request) {
        if (request == null
                || (isBlank(request.situation()) && isBlank(request.task())
                && isBlank(request.action()) && isBlank(request.result()))) {
            throw new BusinessException(400, "请至少填写一项 STAR 内容，AI 不会替你编造经历");
        }
        ResumeDraft draft = draftService.requireOwned(userId, draftId);
        ItemRef ref = findItem(draft.getContent(), experienceId);
        JsonNode before = ref.item();

        StarStructureResult aiResult = aiService.structureStar(before.toString(),
                request.situation(), request.task(), request.action(), request.result());

        ObjectNode candidate = before.deepCopy();
        candidate.set("star", aiResult.star());
        if (aiResult.description() != null && !aiResult.description().isBlank()) {
            candidate.put("description", aiResult.description());
        }

        String sourceText = before.toString() + " " + nullToEmpty(request.situation()) + " "
                + nullToEmpty(request.task()) + " " + nullToEmpty(request.action()) + " "
                + nullToEmpty(request.result());
        CandidateFactGuard.SanitizeResult sanitized = factGuard.sanitize(candidate, before, sourceText);

        List<String> warnings = new ArrayList<>(aiResult.warnings());
        warnings.addAll(sanitized.warnings());

        ResumeEditHistory history = new ResumeEditHistory();
        history.setUserId(userId);
        history.setResumeId(draft.getResumeId());
        history.setDraftId(draft.getId());
        history.setSectionKey(ref.sectionKey());
        history.setBeforeContent(before);
        history.setAfterContent(sanitized.content());
        history.setSource(ResumeEditHistory.SOURCE_AI);
        history.setStatus(ResumeEditHistory.STATUS_PENDING);
        history.setWarnings(warnings);
        history.setPromptSummary(STAR_MARKER + experienceId);
        history.setModel(MODEL_NAME);
        history.setCreatedAt(LocalDateTime.now());
        historyMapper.insert(history);
        return toVO(history);
    }

    /**
     * 用户确认：把候选写入草稿（revision+1），并把历史置为 APPLIED。
     */
    @Transactional
    public ResumeEditHistoryVO confirm(Long userId, Long historyId, ConfirmEditRequest request) {
        ResumeEditHistory history = requireOwned(userId, historyId);
        if (!ResumeEditHistory.STATUS_PENDING.equals(history.getStatus())) {
            throw new BusinessException(409, "该修改已处理，不能重复确认");
        }
        if (history.getDraftId() == null) {
            throw new BusinessException(400, "该候选修改未关联草稿");
        }
        JsonNode finalContent = request == null || request.finalContent() == null
                ? history.getAfterContent()
                : objectMapper.valueToTree(request.finalContent());
        if (isEmptyContent(finalContent)) {
            throw new BusinessException(400, "候选内容为空，已拒绝应用");
        }

        if (isStarCandidate(history)) {
            draftService.applyItem(userId, history.getDraftId(), history.getSectionKey(), finalContent);
        } else {
            draftService.applySection(userId, history.getDraftId(), history.getSectionKey(), finalContent);
        }

        history.setStatus(ResumeEditHistory.STATUS_APPLIED);
        history.setAppliedAt(LocalDateTime.now());
        historyMapper.updateById(history);
        return toVO(history);
    }

    /** 用户拒绝：草稿内容保持不变。 */
    public ResumeEditHistoryVO reject(Long userId, Long historyId) {
        ResumeEditHistory history = requireOwned(userId, historyId);
        if (!ResumeEditHistory.STATUS_PENDING.equals(history.getStatus())) {
            throw new BusinessException(409, "该修改已处理，不能重复拒绝");
        }
        history.setStatus(ResumeEditHistory.STATUS_REJECTED);
        historyMapper.updateById(history);
        return toVO(history);
    }

    public ResumeEditHistoryVO detail(Long userId, Long historyId) {
        return toVO(requireOwned(userId, historyId));
    }

    public List<ResumeEditHistoryVO> listByDraft(Long userId, Long draftId) {
        draftService.requireOwned(userId, draftId);
        List<ResumeEditHistory> list = historyMapper.selectList(
                new LambdaQueryWrapper<ResumeEditHistory>()
                        .eq(ResumeEditHistory::getDraftId, draftId)
                        .eq(ResumeEditHistory::getUserId, userId)
                        .orderByDesc(ResumeEditHistory::getCreatedAt)
                        .orderByDesc(ResumeEditHistory::getId));
        return list.stream().map(this::toVO).toList();
    }

    public List<ResumeEditHistoryVO> listByResume(Long userId, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!Objects.equals(resume.getUserId(), userId)) {
            throw new BusinessException(403, "无权操作该简历");
        }
        List<ResumeEditHistory> list = historyMapper.selectList(
                new LambdaQueryWrapper<ResumeEditHistory>()
                        .eq(ResumeEditHistory::getResumeId, resumeId)
                        .eq(ResumeEditHistory::getUserId, userId)
                        .orderByDesc(ResumeEditHistory::getCreatedAt)
                        .orderByDesc(ResumeEditHistory::getId));
        return list.stream().map(this::toVO).toList();
    }

    private ResumeEditHistory requireOwned(Long userId, Long historyId) {
        ResumeEditHistory history = historyMapper.selectById(historyId);
        if (history == null) {
            throw new BusinessException(404, "修改记录不存在");
        }
        if (!Objects.equals(history.getUserId(), userId)) {
            throw new BusinessException(403, "无权访问该修改记录");
        }
        return history;
    }

    private ItemRef findItem(JsonNode content, String id) {
        for (JsonNode item : content.path("experiences")) {
            if (id.equals(item.path("id").asText())) {
                return new ItemRef("EXPERIENCE", item);
            }
        }
        for (JsonNode item : content.path("projects")) {
            if (id.equals(item.path("id").asText())) {
                return new ItemRef("PROJECT", item);
            }
        }
        throw new BusinessException(404, "经历不存在，请刷新草稿后重试");
    }

    private static boolean isStarCandidate(ResumeEditHistory history) {
        return history.getPromptSummary() != null && history.getPromptSummary().startsWith(STAR_MARKER);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static boolean isEmptyContent(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        if (node.isTextual()) {
            return node.asText().isBlank();
        }
        return (node.isArray() || node.isObject()) && node.isEmpty();
    }

    private ResumeEditHistoryVO toVO(ResumeEditHistory history) {
        return new ResumeEditHistoryVO(
                history.getId(),
                history.getResumeId(),
                history.getDraftId(),
                history.getSectionKey(),
                history.getBeforeContent(),
                history.getAfterContent(),
                history.getSource(),
                history.getStatus(),
                history.getWarnings() == null ? List.of() : history.getWarnings(),
                history.getPromptSummary(),
                history.getModel(),
                history.getCreatedAt(),
                history.getAppliedAt());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ItemRef(String sectionKey, JsonNode item) {
    }
}
