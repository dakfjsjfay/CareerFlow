package com.team.resume.optimization.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.Message;
import com.team.resume.ai.dto.OptimizationReplyResult;
import com.team.resume.chat.entity.ChatMessage;
import com.team.resume.chat.entity.ChatSession;
import com.team.resume.chat.mapper.ChatMessageMapper;
import com.team.resume.chat.mapper.ChatSessionMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.job.entity.Job;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.optimization.dto.CreateOptimizationTaskRequest;
import com.team.resume.optimization.dto.ReplyOptimizationRequest;
import com.team.resume.optimization.entity.OptimizationTask;
import com.team.resume.optimization.mapper.OptimizationTaskMapper;
import com.team.resume.optimization.vo.OptimizationConfirmVO;
import com.team.resume.optimization.vo.OptimizationReplyVO;
import com.team.resume.optimization.vo.OptimizationTaskVO;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.service.EvidenceService;
import com.team.resume.profile.service.EvidenceSource;
import com.team.resume.profile.service.ExtractionEvidenceFactory;
import com.team.resume.profile.service.ProfileDataService;
import com.team.resume.resume.dto.GenerateResumeRequest;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.service.ResumeService;
import com.team.resume.resume.vo.ResumeGenerateResultVO;

/**
 * 反向优化业务（docs/api.md §10，赛题核心亮点）：
 * 差距项点击 → 创建优化任务（复用/新建对话会话，AI 生成第一轮追问）
 * → 多轮追问（消息进 chat_message，抽取进 PENDING 证据）
 * → 用户确认 → 对整段对话一次性抽取 → 证据以 CONFIRMED 直接写档案
 * → 触发简历重新生成（generationReason=OPTIMIZATION，永不覆盖旧版本）
 * → 回到匹配页重新分析即可看到分数变化。
 * <p>
 * 归属校验：任务经 chat_session_id → chat_session.user_id，同时校验任务内
 * resume_version 归属（database.md §8）。
 */
@Service
public class OptimizationService {

    private static final Logger log = LoggerFactory.getLogger(OptimizationService.class);

    private final OptimizationTaskMapper taskMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AIService aiService;
    private final EvidenceService evidenceService;
    private final ExtractionEvidenceFactory extractionFactory;
    private final ProfileDataService profileDataService;
    private final UserProfileMapper userProfileMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeService resumeService;
    private final JobMapper jobMapper;

    public OptimizationService(OptimizationTaskMapper taskMapper,
                               ChatSessionMapper sessionMapper,
                               ChatMessageMapper messageMapper,
                               AIService aiService,
                               EvidenceService evidenceService,
                               ExtractionEvidenceFactory extractionFactory,
                               ProfileDataService profileDataService,
                               UserProfileMapper userProfileMapper,
                               ResumeMapper resumeMapper,
                               ResumeVersionMapper resumeVersionMapper,
                               ResumeService resumeService,
                               JobMapper jobMapper) {
        this.taskMapper = taskMapper;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.aiService = aiService;
        this.evidenceService = evidenceService;
        this.extractionFactory = extractionFactory;
        this.profileDataService = profileDataService;
        this.userProfileMapper = userProfileMapper;
        this.resumeMapper = resumeMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.resumeService = resumeService;
        this.jobMapper = jobMapper;
    }

    /**
     * 创建优化任务：校验岗位/简历版本归属 → 复用或新建对话会话 → AI 生成第一轮追问。
     * 追问失败时降级为确定性追问（追问本身不是档案数据，保证演示链路不因 AI 抖动中断）。
     */
    @Transactional
    public OptimizationTaskVO create(Long userId, CreateOptimizationTaskRequest request) {
        Job job = requireJob(request.getJobId());
        requireOwnedVersion(userId, request.getResumeVersionId());
        String targetSkill = request.getTargetSkill().trim();

        Long sessionId = request.getChatSessionId();
        if (sessionId != null) {
            requireOwnedSession(userId, sessionId);
        } else {
            sessionId = createSession(userId, "技能追问 · " + targetSkill);
        }

        String question;
        try {
            question = aiService.generateOptimizationQuestion(targetSkill, job.getPosition());
        } catch (RuntimeException e) {
            log.warn("AI 生成追问失败（job={}, skill={}），降级为确定性追问", job.getId(), targetSkill, e);
            question = "你学习过「" + targetSkill + "」吗？如果做过相关练习或项目，请介绍一下："
                    + "项目名称、使用了哪些技术、你负责的部分。";
        }

        LocalDateTime now = LocalDateTime.now();
        OptimizationTask task = new OptimizationTask();
        task.setJobId(job.getId());
        task.setResumeVersionId(request.getResumeVersionId());
        task.setChatSessionId(sessionId);
        task.setTargetSkill(targetSkill);
        task.setQuestion(question);
        task.setStatus(OptimizationTask.STATUS_WAITING_USER);
        task.setSource(OptimizationTask.SOURCE_JD_GAP);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        insertAssistantMessage(sessionId, question, now);
        touchSession(sessionId, now);
        return toVO(task);
    }

    public OptimizationTaskVO detail(Long userId, Long taskId) {
        return toVO(requireOwnedTask(userId, taskId));
    }

    /**
     * 回复 AI 追问：AI 成功后才落用户消息与助手回复（与建档对话同规则，AI 失败不落库）。
     * 本轮抽取的新信息落 PENDING 证据，等待最终 confirm。
     */
    @Transactional
    public OptimizationReplyVO reply(Long userId, Long taskId, ReplyOptimizationRequest request) {
        OptimizationTask task = requireOwnedTask(userId, taskId);
        requireOpenForReply(task);

        String answer = request.getAnswer().trim();
        List<ChatMessage> chronological = listMessages(task.getChatSessionId());
        List<Message> history = chronological.stream()
                .map(m -> new Message(
                        ChatMessage.ROLE_USER.equals(m.getRole()) ? "user" : "assistant",
                        m.getContent()))
                .toList();

        OptimizationReplyResult result =
                aiService.optimizationReply(history, answer, task.getTargetSkill());

        LocalDateTime now = LocalDateTime.now();
        ChatMessage userMessage = insertMessage(task.getChatSessionId(),
                ChatMessage.ROLE_USER, answer, now);
        insertMessage(task.getChatSessionId(), ChatMessage.ROLE_ASSISTANT, result.reply(), now);

        // 本轮抽取 → PENDING 证据；来源定位到真正说过这句话的用户消息
        List<EvidenceSource> sources = new ArrayList<>();
        for (ChatMessage message : chronological) {
            if (ChatMessage.ROLE_USER.equals(message.getRole())) {
                sources.add(new EvidenceSource(message.getId(), message.getContent()));
            }
        }
        sources.add(new EvidenceSource(userMessage.getId(), answer));
        evidenceService.createPendingFromExtraction(userId, sources, result.extractedInfo());

        String newStatus = result.nextQuestion()
                ? OptimizationTask.STATUS_WAITING_USER
                : OptimizationTask.STATUS_USER_CONFIRMATION;
        task.setStatus(newStatus);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        touchSession(task.getChatSessionId(), now);

        return new OptimizationReplyVO(result.reply(), result.nextQuestion(),
                newStatus, result.extractedInfo(), result.missingFields());
    }

    /**
     * 确认补充信息：对整段追问对话一次性抽取 → 事实校验 → 证据以 CONFIRMED 写档案
     * → 触发简历重新生成（OPTIMIZATION）→ 任务结束。
     * 任一步失败（含 AI 502）整体回滚，任务保持原状态可重试。
     */
    @Transactional
    public OptimizationConfirmVO confirm(Long userId, Long taskId) {
        OptimizationTask task = requireOwnedTask(userId, taskId);
        if (OptimizationTask.STATUS_RESUME_REGENERATED.equals(task.getStatus())) {
            throw new BusinessException(409, "该任务已完成，无需重复确认");
        }
        if (!OptimizationTask.STATUS_USER_CONFIRMATION.equals(task.getStatus())
                && !OptimizationTask.STATUS_WAITING_USER.equals(task.getStatus())) {
            throw new BusinessException(409, "当前状态不能确认：" + task.getStatus());
        }

        List<ChatMessage> chronological = listMessages(task.getChatSessionId());
        List<EvidenceSource> sources = chronological.stream()
                .filter(m -> ChatMessage.ROLE_USER.equals(m.getRole()))
                .map(m -> new EvidenceSource(m.getId(), m.getContent()))
                .toList();
        if (sources.isEmpty()) {
            throw new BusinessException(400, "还没有任何回复，无法确认补充信息");
        }

        List<Message> history = chronological.stream()
                .map(m -> new Message(
                        ChatMessage.ROLE_USER.equals(m.getRole()) ? "user" : "assistant",
                        m.getContent()))
                .toList();
        ChatExtractionResult extraction =
                aiService.extractOptimizationInfo(history, task.getTargetSkill());

        // 抽取 → 判重 → 只处理「此前未确认过」的新信息
        Set<String> existingKeys = evidenceService.existingKeys(userId);
        Set<String> seen = new LinkedHashSet<>();
        List<ProfileEvidence> fresh = new ArrayList<>();
        for (ProfileEvidence candidate : extractionFactory.build(userId, sources,
                extraction.extractedInfo())) {
            String key = EvidenceService.dedupeKey(candidate);
            if (!existingKeys.contains(key) && seen.add(key)) {
                fresh.add(candidate);
            }
        }
        if (fresh.isEmpty()) {
            throw new BusinessException(400, "对话中没有新的补充信息（可能已在之前确认过）");
        }

        UserProfile profile = profileDataService.ensureProfile(userId);
        boolean profileDirty = false;
        Set<String> updatedFields = new LinkedHashSet<>();
        for (ProfileEvidence evidence : fresh) {
            profileDirty |= evidenceService.insertConfirmed(evidence, userId, profile, updatedFields);
        }
        if (profileDirty) {
            userProfileMapper.updateById(profile);
        }

        ResumeGenerateResultVO generated = regenerateResume(userId, task);

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(OptimizationTask.STATUS_RESUME_REGENERATED);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);

        insertAssistantMessage(task.getChatSessionId(),
                "已完成：本次补充的 " + fresh.size() + " 条信息已写入档案"
                        + (updatedFields.isEmpty() ? "" : "（" + String.join("、", updatedFields) + "）")
                        + "，并生成了新简历版本 v" + generated.versionNumber()
                        + "。回到匹配分析页重新分析即可查看分数变化。",
                now);
        touchSession(task.getChatSessionId(), now);

        return new OptimizationConfirmVO(taskId, task.getStatus(), task.getJobId(),
                generated.resumeId(), generated.versionId(), generated.versionNumber(),
                fresh.size(), List.copyOf(updatedFields));
    }

    // ---------- 内部工具 ----------

    private ResumeGenerateResultVO regenerateResume(Long userId, OptimizationTask task) {
        ResumeVersion oldVersion = requireOwnedVersion(userId, task.getResumeVersionId());
        Resume resume = resumeMapper.selectById(oldVersion.getResumeId());
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        String templateId = templateIdOf(oldVersion.getContent());
        if (templateId == null) {
            throw new BusinessException(400, "原简历版本缺少模板信息，无法重新生成");
        }
        GenerateResumeRequest request = new GenerateResumeRequest();
        request.setTemplateId(templateId);
        request.setTargetJobId(task.getJobId());
        request.setGenerationReason("OPTIMIZATION");
        return resumeService.generate(userId, resume.getId(), request);
    }

    private OptimizationTask requireOwnedTask(Long userId, Long taskId) {
        OptimizationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "优化任务不存在");
        }
        if (task.getChatSessionId() == null) {
            throw new BusinessException(403, "无权访问该任务");
        }
        ChatSession session = sessionMapper.selectById(task.getChatSessionId());
        if (session == null || !Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(403, "无权访问该任务");
        }
        return task;
    }

    private void requireOpenForReply(OptimizationTask task) {
        if (!OptimizationTask.STATUS_WAITING_USER.equals(task.getStatus())
                && !OptimizationTask.STATUS_USER_CONFIRMATION.equals(task.getStatus())) {
            throw new BusinessException(409,
                    "该任务当前不能继续追问（状态：" + task.getStatus() + "）");
        }
    }

    private void requireOwnedSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException(403, "无权访问该会话");
        }
    }

    private Long createSession(Long userId, String title) {
        LocalDateTime now = LocalDateTime.now();
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title);
        session.setStatus("ACTIVE");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);
        return session.getId();
    }

    private ResumeVersion requireOwnedVersion(Long userId, Long versionId) {
        ResumeVersion version = resumeVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        Resume resume = resumeMapper.selectById(version.getResumeId());
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(403, "该简历版本不属于当前用户");
        }
        return version;
    }

    private Job requireJob(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return job;
    }

    private List<ChatMessage> listMessages(Long sessionId) {
        List<ChatMessage> rows = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
        return rows == null ? Collections.emptyList() : rows;
    }

    private ChatMessage insertMessage(Long sessionId, String role, String content,
                                      LocalDateTime createdAt) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(createdAt);
        messageMapper.insert(message);
        return message;
    }

    private void insertAssistantMessage(Long sessionId, String content, LocalDateTime createdAt) {
        insertMessage(sessionId, ChatMessage.ROLE_ASSISTANT, content, createdAt);
    }

    private void touchSession(Long sessionId, LocalDateTime now) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(now);
            sessionMapper.updateById(session);
        }
    }

    private String templateIdOf(JsonNode content) {
        if (content == null) {
            return null;
        }
        JsonNode templateId = content.path("meta").path("templateId");
        return templateId.isMissingNode() || templateId.isNull() ? null : templateId.asText();
    }

    private static OptimizationTaskVO toVO(OptimizationTask task) {
        OptimizationTaskVO vo = new OptimizationTaskVO();
        vo.setId(task.getId());
        vo.setJobId(task.getJobId());
        vo.setResumeVersionId(task.getResumeVersionId());
        vo.setChatSessionId(task.getChatSessionId());
        vo.setTargetSkill(task.getTargetSkill());
        vo.setQuestion(task.getQuestion());
        vo.setStatus(task.getStatus());
        vo.setSource(task.getSource());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }
}
