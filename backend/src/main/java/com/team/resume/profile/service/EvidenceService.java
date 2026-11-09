package com.team.resume.profile.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.team.resume.chat.entity.ChatMessage;
import com.team.resume.chat.mapper.ChatMessageMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.dto.ConfirmEvidenceRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.vo.ConfirmEvidenceResult;
import com.team.resume.profile.vo.EvidenceVO;

/**
 * 证据链业务（D4）：
 * - 对话抽取结果先落 profile_evidence（PENDING），实体表不写入；
 * - 用户确认后才写入档案实体表，并把 entity_id 回填到证据；
 * - 拒绝只改状态，不写入任何档案数据。
 * <p>
 * 归属校验：所有读写都以当前登录 user_id 为准，不信任请求里的任何归属信息。
 */
@Service
public class EvidenceService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            ProfileEvidence.STATUS_PENDING,
            ProfileEvidence.STATUS_CONFIRMED,
            ProfileEvidence.STATUS_REJECTED);

    private final ProfileEvidenceMapper evidenceMapper;
    private final UserProfileMapper userProfileMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ExtractionEvidenceFactory extractionFactory;
    private final ProfileDataService profileDataService;

    public EvidenceService(ProfileEvidenceMapper evidenceMapper,
                           UserProfileMapper userProfileMapper,
                           ChatMessageMapper chatMessageMapper,
                           ExtractionEvidenceFactory extractionFactory,
                           ProfileDataService profileDataService) {
        this.evidenceMapper = evidenceMapper;
        this.userProfileMapper = userProfileMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.extractionFactory = extractionFactory;
        this.profileDataService = profileDataService;
    }

    /**
     * 对话成功后调用：把本次 AI 抽取结果落为 PENDING 证据。
     * 与 ChatService.send 在同一事务中；任何异常都会连同消息一起回滚。
     */
    @Transactional
    public void createPendingFromExtraction(Long userId, Long sourceMessageId,
                                            JsonNode extractedInfo, String sourceText) {
        List<ProfileEvidence> candidates =
                extractionFactory.build(userId, sourceMessageId, extractedInfo, sourceText);
        if (candidates.isEmpty()) {
            return;
        }
        // 已确认的同样信息不再重复生成 PENDING；被拒绝的允许用户再次确认
        Set<String> existingKeys = evidenceMapper.selectList(new LambdaQueryWrapper<ProfileEvidence>()
                        .eq(ProfileEvidence::getUserId, userId)
                        .in(ProfileEvidence::getConfirmationStatus,
                                ProfileEvidence.STATUS_PENDING, ProfileEvidence.STATUS_CONFIRMED))
                .stream()
                .map(EvidenceService::dedupeKey)
                .collect(Collectors.toSet());

        for (ProfileEvidence candidate : candidates) {
            // 同一用户已有完全相同的待确认证据时不重复插入，避免多轮对话刷屏
            if (existingKeys.add(dedupeKey(candidate))) {
                evidenceMapper.insert(candidate);
            }
        }
    }

    /** 证据列表：默认 PENDING，可按状态筛选。 */
    public List<EvidenceVO> list(Long userId, String status) {
        String normalized = normalizeStatus(status);
        List<ProfileEvidence> rows = evidenceMapper.selectList(new LambdaQueryWrapper<ProfileEvidence>()
                .eq(ProfileEvidence::getUserId, userId)
                .eq(ProfileEvidence::getConfirmationStatus, normalized)
                .orderByDesc(ProfileEvidence::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> sourceContents = loadSourceContents(rows);
        return rows.stream()
                .map(row -> new EvidenceVO(
                        row.getId(),
                        row.getEntityType(),
                        row.getEntityId(),
                        row.getFieldName(),
                        row.getSourceType(),
                        row.getSourceId(),
                        sourceContents.get(row.getSourceId()),
                        row.getEvidenceContent(),
                        row.getConfirmationStatus(),
                        row.getCreatedAt()))
                .toList();
    }

    /**
     * 确认或拒绝证据。
     * 先校验全部证据（不存在/非本人/已处理）再写入，保证一次请求要么全部生效要么全部回滚。
     */
    @Transactional
    public ConfirmEvidenceResult confirm(Long userId, ConfirmEvidenceRequest request) {
        List<Long> ids = request.getEvidenceIds() == null
                ? List.of()
                : request.getEvidenceIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException(400, "证据 ID 不能为空");
        }

        List<ProfileEvidence> evidenceList = new ArrayList<>(ids.size());
        for (Long id : ids) {
            ProfileEvidence evidence = evidenceMapper.selectById(id);
            if (evidence == null) {
                throw new BusinessException(404, "证据不存在：" + id);
            }
            if (!Objects.equals(evidence.getUserId(), userId)) {
                throw new BusinessException(403, "无权操作该证据：" + id);
            }
            if (!ProfileEvidence.STATUS_PENDING.equals(evidence.getConfirmationStatus())) {
                throw new BusinessException(409,
                        "证据 " + id + " 已处理（当前状态：" + evidence.getConfirmationStatus() + "）");
            }
            evidenceList.add(evidence);
        }

        if (!Boolean.TRUE.equals(request.getConfirmed())) {
            for (ProfileEvidence evidence : evidenceList) {
                markProcessed(evidence.getId(), userId, ProfileEvidence.STATUS_REJECTED, null);
            }
            return new ConfirmEvidenceResult(0, List.of());
        }

        UserProfile profile = profileDataService.ensureProfile(userId);
        boolean profileDirty = false;
        int confirmedCount = 0;
        Set<String> updatedFields = new LinkedHashSet<>();

        for (ProfileEvidence evidence : evidenceList) {
            if (evidence.getEntityType() == null) {
                throw new BusinessException(400, "证据类型为空：" + evidence.getId());
            }
            switch (evidence.getEntityType()) {
                case ProfileEvidence.ENTITY_PROFILE -> {
                    if (profileDataService.applyProfileField(
                            profile, evidence.getFieldName(), evidence.getEvidenceContent())) {
                        updatedFields.add(apiFieldName(evidence.getFieldName()));
                        profileDirty = true;
                    }
                    markProcessed(evidence.getId(), userId,
                            ProfileEvidence.STATUS_CONFIRMED, profile.getId());
                }
                case ProfileEvidence.ENTITY_SKILL -> {
                    if (profileDataService.addSkill(profile, evidence.getEvidenceContent())) {
                        updatedFields.add(ExtractionEvidenceFactory.FIELD_SKILLS);
                        profileDirty = true;
                    }
                    markProcessed(evidence.getId(), userId,
                            ProfileEvidence.STATUS_CONFIRMED, profile.getId());
                }
                case ProfileEvidence.ENTITY_PROJECT -> {
                    ProfileProject project = profileDataService.createProject(
                            profile.getId(), evidence.getEvidenceContent());
                    addProjectFields(updatedFields, project);
                    markProcessed(evidence.getId(), userId,
                            ProfileEvidence.STATUS_CONFIRMED, project.getId());
                }
                case ProfileEvidence.ENTITY_EXPERIENCE -> {
                    ProfileExperience experience = profileDataService.createExperience(
                            profile.getId(), evidence.getEvidenceContent());
                    addExperienceFields(updatedFields, experience);
                    markProcessed(evidence.getId(), userId,
                            ProfileEvidence.STATUS_CONFIRMED, experience.getId());
                }
                default -> throw new BusinessException(400,
                        "不支持的证据类型：" + evidence.getEntityType());
            }
            confirmedCount++;
        }

        if (profileDirty) {
            userProfileMapper.updateById(profile);
        }
        return new ConfirmEvidenceResult(confirmedCount, List.copyOf(updatedFields));
    }

    /**
     * 条件更新：只有仍为 PENDING 且属于当前用户的证据才会更新。
     * 返回 0 说明被并发请求抢先处理，抛 409 让整个事务回滚。
     */
    private void markProcessed(Long id, Long userId, String status, Long entityId) {
        int updated = evidenceMapper.markProcessed(id, userId, status, entityId);
        if (updated != 1) {
            throw new BusinessException(409, "证据 " + id + " 已被处理");
        }
    }

    private Map<Long, String> loadSourceContents(List<ProfileEvidence> rows) {
        Set<Long> chatSourceIds = rows.stream()
                .filter(row -> ProfileEvidence.SOURCE_CHAT.equals(row.getSourceType()))
                .map(ProfileEvidence::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (chatSourceIds.isEmpty()) {
            return Map.of();
        }
        return chatMessageMapper.selectBatchIds(chatSourceIds).stream()
                .collect(Collectors.toMap(ChatMessage::getId, ChatMessage::getContent, (a, b) -> a));
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? ProfileEvidence.STATUS_PENDING
                : status.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "status 只能是 PENDING/CONFIRMED/REJECTED");
        }
        return normalized;
    }

    /** 响应中的字段路径统一用 API camelCase（数据库 field_name 为 snake_case）。 */
    private static String apiFieldName(String fieldName) {
        return switch (fieldName) {
            case "real_name" -> "realName";
            case "graduation_year" -> "graduationYear";
            case "target_position" -> "targetPosition";
            case "target_city" -> "targetCity";
            case "expected_salary" -> "expectedSalary";
            default -> fieldName;
        };
    }

    /** 同一用户、同一字段、同样内容视为同一条证据。 */
    private static String dedupeKey(ProfileEvidence evidence) {
        return evidence.getEntityType() + "|" + evidence.getFieldName() + "|" + evidence.getEvidenceContent();
    }

    private static void addProjectFields(Set<String> fields, ProfileProject project) {
        fields.add("project.projectName");
        if (hasText(project.getRole())) {
            fields.add("project.role");
        }
        if (hasText(project.getDescription())) {
            fields.add("project.description");
        }
        if (project.getTechStack() != null && !project.getTechStack().isEmpty()) {
            fields.add("project.techStack");
        }
        if (project.getStartDate() != null) {
            fields.add("project.startDate");
        }
        if (project.getEndDate() != null) {
            fields.add("project.endDate");
        }
    }

    private static void addExperienceFields(Set<String> fields, ProfileExperience experience) {
        fields.add("experience.company");
        if (hasText(experience.getPosition())) {
            fields.add("experience.position");
        }
        if (hasText(experience.getDescription())) {
            fields.add("experience.description");
        }
        if (hasText(experience.getAchievements())) {
            fields.add("experience.achievements");
        }
        if (experience.getStartDate() != null) {
            fields.add("experience.startDate");
        }
        if (experience.getEndDate() != null) {
            fields.add("experience.endDate");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
