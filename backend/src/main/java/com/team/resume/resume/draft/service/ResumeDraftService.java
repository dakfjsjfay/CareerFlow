package com.team.resume.resume.draft.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.draft.dto.CompleteDraftRequest;
import com.team.resume.resume.draft.dto.CreateDraftRequest;
import com.team.resume.resume.draft.dto.SaveDraftRequest;
import com.team.resume.resume.draft.entity.ResumeDraft;
import com.team.resume.resume.draft.mapper.ResumeDraftMapper;
import com.team.resume.resume.draft.vo.DraftCompleteVO;
import com.team.resume.resume.draft.vo.ResumeDraftVO;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.service.ResumeVersionService;
import com.team.resume.resume.vo.ResumeContent;

/**
 * 八步草稿服务。
 * <p>
 * 关键约束：
 * 1. 所有读写按 user_id 隔离，非本人一律 403；
 * 2. 保存使用 revision 乐观锁，冲突返回 409，绝不静默覆盖；
 * 3. 完成草稿只新增不可变版本，不修改任何历史版本。
 */
@Service
public class ResumeDraftService {

    private static final int TOTAL_STEPS = 8;
    private static final List<String> STATUSES = List.of(
            ResumeDraft.STATUS_DRAFT, ResumeDraft.STATUS_COMPLETED, ResumeDraft.STATUS_ABANDONED);

    private final ResumeDraftMapper draftMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionService versionService;
    private final DraftContentConverter converter;
    private final StarFollowUpAdvisor followUpAdvisor;
    private final ObjectMapper objectMapper;

    public ResumeDraftService(ResumeDraftMapper draftMapper,
                              ResumeMapper resumeMapper,
                              ResumeVersionService versionService,
                              DraftContentConverter converter,
                              StarFollowUpAdvisor followUpAdvisor,
                              ObjectMapper objectMapper) {
        this.draftMapper = draftMapper;
        this.resumeMapper = resumeMapper;
        this.versionService = versionService;
        this.converter = converter;
        this.followUpAdvisor = followUpAdvisor;
        this.objectMapper = objectMapper;
    }

    public ResumeDraftVO create(Long userId, CreateDraftRequest request) {
        if (request != null && request.resumeId() != null) {
            requireResume(userId, request.resumeId());
        }
        ResumeDraft draft = new ResumeDraft();
        draft.setUserId(userId);
        draft.setResumeId(request == null ? null : request.resumeId());
        draft.setDraftName(resolveName(request == null ? null : request.draftName()));
        draft.setCurrentStep(1);
        draft.setContent(normalizeContent(request == null ? null : request.content()));
        draft.setRevision(1);
        draft.setStatus(ResumeDraft.STATUS_DRAFT);
        LocalDateTime now = LocalDateTime.now();
        draft.setLastSavedAt(now);
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draftMapper.insert(draft);
        return toVO(draft);
    }

    public List<ResumeDraftVO> list(Long userId, String status) {
        String resolved = status == null || status.isBlank() ? ResumeDraft.STATUS_DRAFT : status;
        if (!STATUSES.contains(resolved)) {
            throw new BusinessException(400, "不支持的草稿状态：" + resolved);
        }
        List<ResumeDraft> drafts = draftMapper.selectList(new LambdaQueryWrapper<ResumeDraft>()
                .eq(ResumeDraft::getUserId, userId)
                .eq(ResumeDraft::getStatus, resolved)
                .orderByDesc(ResumeDraft::getLastSavedAt)
                .orderByDesc(ResumeDraft::getId));
        return drafts.stream().map(this::toVO).toList();
    }

    public ResumeDraftVO detail(Long userId, Long id) {
        return toVO(requireOwned(userId, id));
    }

    /**
     * 全量自动保存。返回 409 的三种情况：草稿已结束、revision 过期、无权访问。
     */
    public ResumeDraftVO save(Long userId, Long id, SaveDraftRequest request) {
        if (request == null || request.revision() == null) {
            throw new BusinessException(400, "缺少 revision，无法保存草稿");
        }
        int step = request.currentStep() == null ? 1 : request.currentStep();
        if (step < 1 || step > TOTAL_STEPS) {
            throw new BusinessException(400, "步骤超出范围");
        }
        JsonNode content = normalizeContent(request.content());
        int updated = draftMapper.updateContent(id, userId, request.revision(), step,
                writeContent(content), blankToNull(request.draftName()));
        if (updated == 0) {
            ResumeDraft existing = draftMapper.selectById(id);
            if (existing == null) {
                throw new BusinessException(404, "草稿不存在");
            }
            if (!Objects.equals(existing.getUserId(), userId)) {
                throw new BusinessException(403, "无权访问该草稿");
            }
            if (!ResumeDraft.STATUS_DRAFT.equals(existing.getStatus())) {
                throw new BusinessException(409, "草稿已完成或已放弃，不能继续保存");
            }
            throw new BusinessException(409, "草稿已在其他页面更新，请刷新后重试");
        }
        return toVO(draftMapper.selectById(id));
    }

    public void abandon(Long userId, Long id) {
        int updated = draftMapper.updateStatus(id, userId, ResumeDraft.STATUS_ABANDONED, null, null);
        if (updated == 0) {
            requireOwned(userId, id);
            throw new BusinessException(409, "草稿已完成或已放弃");
        }
    }

    /**
     * 第八步：校验草稿 → 生成简历主体（首次）→ 写入全新不可变版本 → 标记草稿完成。
     */
    @Transactional
    public DraftCompleteVO complete(Long userId, Long id, CompleteDraftRequest request) {
        ResumeDraft draft = requireOwned(userId, id);
        if (!ResumeDraft.STATUS_DRAFT.equals(draft.getStatus())) {
            throw new BusinessException(409, "草稿已完成或已放弃，不能重复生成");
        }
        converter.validateForComplete(draft.getContent());

        Long resumeId = draft.getResumeId();
        if (resumeId == null) {
            Resume resume = new Resume();
            resume.setUserId(userId);
            resume.setResumeName(resolveResumeName(request, draft));
            LocalDateTime now = LocalDateTime.now();
            resume.setCreatedAt(now);
            resume.setUpdatedAt(now);
            resumeMapper.insert(resume);
            resumeId = resume.getId();
        } else {
            requireResume(userId, resumeId);
        }

        String templateId = request == null ? null : request.templateId();
        String reason = request == null || request.generationReason() == null
                || request.generationReason().isBlank() ? "INITIAL" : request.generationReason();
        if (!List.of("INITIAL", "JOB_TARGETED", "OPTIMIZATION", "REGENERATE").contains(reason)) {
            throw new BusinessException(400, "不支持的生成原因：" + reason);
        }
        ResumeContent content = converter.toResumeContent(draft.getContent(), templateId, reason);
        JsonNode versionContent = converter.buildVersionContent(content, draft.getContent());
        ResumeVersion version = versionService.createRaw(userId, resumeId, versionContent, reason);

        int updated = draftMapper.updateStatus(id, userId, ResumeDraft.STATUS_COMPLETED, resumeId, TOTAL_STEPS);
        if (updated == 0) {
            throw new BusinessException(409, "草稿状态已变更，请刷新后重试");
        }
        return new DraftCompleteVO(resumeId, version.getId(), version.getVersionNumber(), id);
    }

    /** 供区块分析/候选确认使用的归属校验。 */
    public ResumeDraft requireOwned(Long userId, Long id) {
        ResumeDraft draft = draftMapper.selectById(id);
        if (draft == null) {
            throw new BusinessException(404, "草稿不存在");
        }
        if (!Objects.equals(draft.getUserId(), userId)) {
            throw new BusinessException(403, "无权访问该草稿");
        }
        return draft;
    }

    /** 读取指定区块内容（分析/改写前的前置快照）。 */
    public JsonNode sectionContent(Long userId, Long draftId, String sectionKey) {
        return converter.section(requireOwned(userId, draftId).getContent(), sectionKey);
    }

    /** 经历智能追问：按 STAR 缺失项生成确定性追问，不调用 AI。 */
    public JsonNode experienceFollowUps(Long userId, Long draftId, String experienceId) {
        ResumeDraft draft = requireOwned(userId, draftId);
        for (String field : new String[] {"experiences", "projects"}) {
            for (JsonNode item : draft.getContent().path(field)) {
                if (experienceId.equals(item.path("id").asText())) {
                    return followUpAdvisor.advise(experienceId, item);
                }
            }
        }
        throw new BusinessException(404, "经历不存在，请刷新草稿后重试");
    }

    /**
     * 用户确认候选后把整块内容写回草稿，revision+1。
     */
    public ResumeDraftVO applySection(Long userId, Long draftId, String sectionKey, JsonNode afterContent) {
        ResumeDraft draft = requireOwned(userId, draftId);
        if (!ResumeDraft.STATUS_DRAFT.equals(draft.getStatus())) {
            throw new BusinessException(409, "草稿已完成，不能修改");
        }
        ObjectNode content = copyObject(draft.getContent());
        converter.applySection(content, sectionKey, afterContent);
        return persistApplied(userId, draft, content);
    }

    /**
     * 用户确认后替换单条经历/项目（STAR 结构化场景），revision+1。
     */
    public ResumeDraftVO applyItem(Long userId, Long draftId, String sectionKey, JsonNode afterItem) {
        ResumeDraft draft = requireOwned(userId, draftId);
        if (!ResumeDraft.STATUS_DRAFT.equals(draft.getStatus())) {
            throw new BusinessException(409, "草稿已完成，不能修改");
        }
        ObjectNode content = copyObject(draft.getContent());
        converter.applyItem(content, sectionKey, afterItem);
        return persistApplied(userId, draft, content);
    }

    private ResumeDraftVO persistApplied(Long userId, ResumeDraft draft, ObjectNode content) {
        int updated = draftMapper.updateContent(draft.getId(), userId, draft.getRevision(),
                draft.getCurrentStep(), writeContent(content), null);
        if (updated == 0) {
            throw new BusinessException(409, "草稿已在其他页面更新，请刷新后重试");
        }
        return toVO(draftMapper.selectById(draft.getId()));
    }

    public ObjectNode copyObject(JsonNode content) {
        if (content == null || !content.isObject()) {
            return objectMapper.createObjectNode();
        }
        return content.deepCopy();
    }

    public ResumeDraftVO toVO(ResumeDraft draft) {
        return new ResumeDraftVO(
                draft.getId(),
                draft.getResumeId(),
                draft.getDraftName(),
                draft.getCurrentStep(),
                TOTAL_STEPS,
                draft.getRevision(),
                draft.getStatus(),
                draft.getLastSavedAt(),
                draft.getContent() == null ? objectMapper.createObjectNode() : draft.getContent());
    }

    private Resume requireResume(Long userId, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!Objects.equals(resume.getUserId(), userId)) {
            throw new BusinessException(403, "无权操作该简历");
        }
        return resume;
    }

    private JsonNode normalizeContent(JsonNode content) {
        return content == null || content.isNull() ? objectMapper.createObjectNode() : content;
    }

    private String writeContent(JsonNode content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "草稿内容格式不合法");
        }
    }

    private static String resolveName(String draftName) {
        return draftName == null || draftName.isBlank() ? "未命名草稿" : draftName.trim();
    }

    private static String resolveResumeName(CompleteDraftRequest request, ResumeDraft draft) {
        if (request != null && request.resumeName() != null && !request.resumeName().isBlank()) {
            return request.resumeName().trim();
        }
        if (draft.getDraftName() != null && !draft.getDraftName().isBlank()
                && !"未命名草稿".equals(draft.getDraftName())) {
            return draft.getDraftName();
        }
        return "我的简历";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
