package com.team.resume.resume.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.service.ProfileService;
import com.team.resume.profile.vo.ProfileVO;
import com.team.resume.resume.dto.CreateResumeRequest;
import com.team.resume.resume.dto.GenerateResumeRequest;
import com.team.resume.resume.dto.SaveVersionRequest;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.vo.ResumeContent;
import com.team.resume.resume.vo.ResumeDetailVO;
import com.team.resume.resume.vo.ResumeGenerateResultVO;
import com.team.resume.resume.vo.ResumeSummaryVO;
import com.team.resume.resume.vo.ResumeVersionSummaryVO;
import com.team.resume.resume.vo.ResumeVersionVO;

/**
 * 简历主体 CRUD（docs/api.md §7）。
 * 版本相关读写委托给 {@link ResumeVersionService}，保证归属校验只有一处实现。
 */
@Service
public class ResumeService {

    private static final String DEFAULT_RESUME_NAME = "我的简历";
    private static final int MAX_RESUME_NAME_LENGTH = 100;
    private static final int MAX_TEMPLATE_ID_LENGTH = 64;
    private static final Set<String> GENERATE_REASONS =
            Set.of("INITIAL", "JOB_TARGETED", "OPTIMIZATION", "REGENERATE");
    private static final Set<String> SAVE_REASONS =
            Set.of("MANUAL_EDIT", "TEMPLATE_SWITCH", "RESTORE");

    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;
    private final ResumeVersionService versionService;
    private final ProfileService profileService;
    private final ResumeContentAssembler assembler;
    private final ResumeAiService aiService;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeMapper resumeMapper,
                         ResumeVersionMapper versionMapper,
                         ResumeVersionService versionService,
                         ProfileService profileService,
                         ResumeContentAssembler assembler,
                         ResumeAiService aiService,
                         ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.versionMapper = versionMapper;
        this.versionService = versionService;
        this.profileService = profileService;
        this.assembler = assembler;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResumeSummaryVO create(Long userId, CreateResumeRequest request) {
        String name = request == null ? null : request.getResumeName();
        name = name == null ? null : name.trim();
        if (name == null || name.isEmpty()) {
            name = DEFAULT_RESUME_NAME;
        }
        if (name.length() > MAX_RESUME_NAME_LENGTH) {
            throw new BusinessException(400, "简历名称不能超过 100 字");
        }

        LocalDateTime now = LocalDateTime.now();
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setResumeName(name);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.insert(resume);
        return toSummary(resume, null);
    }

    public List<ResumeSummaryVO> list(Long userId) {
        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getUpdatedAt));
        return resumes.stream().map(resume -> {
            ResumeVersion version = currentVersion(resume);
            return toSummary(resume, version);
        }).toList();
    }

    public ResumeDetailVO detail(Long userId, Long resumeId) {
        Resume resume = requireOwned(userId, resumeId);
        ResumeVersion version = currentVersion(resume);
        return new ResumeDetailVO(
                resume.getId(),
                resume.getResumeName(),
                resume.getCurrentVersionId(),
                version == null ? null : version.getVersionNumber(),
                version == null ? null : toVersionVO(version));
    }

    public List<ResumeVersionSummaryVO> listVersions(Long userId, Long resumeId) {
        return versionService.list(userId, resumeId);
    }

    public ResumeVersionVO getVersion(Long userId, Long resumeId, Long versionId) {
        return versionService.get(userId, resumeId, versionId);
    }

    /**
     * AI 生成新版本：Java 组装事实 → AI 改写文案 → 事实校验 → 写入新版本。
     * AI 调用失败直接 502，不产生半成品版本。
     */
    public ResumeGenerateResultVO generate(Long userId, Long resumeId, GenerateResumeRequest request) {
        Resume resume = requireOwned(userId, resumeId);
        if (request == null) {
            throw new BusinessException(400, "请求不能为空");
        }
        String templateId = validateTemplateId(request.getTemplateId());
        String reason = request.getGenerationReason();
        if (reason == null || !GENERATE_REASONS.contains(reason)) {
            throw new BusinessException(400, "生成原因不合法");
        }

        ProfileVO profile = profileService.getProfile(userId);
        ResumeContent base = assembler.assemble(profile, resume.getResumeName(), templateId,
                null, reason, request.getTargetJobId());
        ResumeContent enhanced = aiService.enhance(base, profile, base.meta().warnings());
        ResumeVersion version = versionService.create(userId, resumeId, enhanced, reason);
        return new ResumeGenerateResultVO(
                resumeId, version.getId(), version.getVersionNumber(), version.getContent());
    }

    /**
     * 手动保存新版本：编辑内容 / 切换模板 / 从旧版本恢复，都只新增版本。
     */
    public ResumeGenerateResultVO saveVersion(Long userId, Long resumeId, SaveVersionRequest request) {
        requireOwned(userId, resumeId);
        if (request == null || request.getContent() == null) {
            throw new BusinessException(400, "简历内容不能为空");
        }
        String templateId = validateTemplateId(request.getTemplateId());
        String reason = request.getGenerationReason();
        if (reason == null || !SAVE_REASONS.contains(reason)) {
            throw new BusinessException(400, "保存原因不合法");
        }

        ResumeContent parsed;
        try {
            parsed = objectMapper.treeToValue(request.getContent(), ResumeContent.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, "简历内容格式错误");
        }
        if (parsed == null) {
            throw new BusinessException(400, "简历内容格式错误");
        }

        ResumeContent.Meta oldMeta = parsed.meta();
        ResumeContent.Meta meta = new ResumeContent.Meta(
                templateId,
                oldMeta == null ? null : oldMeta.templateName(),
                LocalDateTime.now().toString(),
                "MANUAL",
                reason,
                oldMeta == null ? null : oldMeta.targetJobId(),
                oldMeta == null || oldMeta.warnings() == null
                        ? new ArrayList<>()
                        : oldMeta.warnings());
        ResumeContent content = new ResumeContent(meta, parsed.personalInfo(), parsed.jobIntention(),
                parsed.education(), parsed.skills(), parsed.projects(), parsed.experiences(),
                parsed.selfEvaluation());

        ResumeVersion version = versionService.create(userId, resumeId, content, reason);
        return new ResumeGenerateResultVO(
                resumeId, version.getId(), version.getVersionNumber(), version.getContent());
    }

    public Resume requireOwned(Long userId, Long resumeId) {
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!Objects.equals(resume.getUserId(), userId)) {
            throw new BusinessException(403, "无权操作该简历");
        }
        return resume;
    }

    private static String validateTemplateId(String templateId) {
        String id = templateId == null ? null : templateId.trim();
        if (id == null || id.isEmpty() || id.length() > MAX_TEMPLATE_ID_LENGTH) {
            throw new BusinessException(400, "模板 ID 不合法");
        }
        return id;
    }

    private ResumeVersion currentVersion(Resume resume) {
        if (resume.getCurrentVersionId() == null) {
            return null;
        }
        return versionMapper.selectById(resume.getCurrentVersionId());
    }

    private ResumeSummaryVO toSummary(Resume resume, ResumeVersion version) {
        return new ResumeSummaryVO(
                resume.getId(),
                resume.getResumeName(),
                resume.getCurrentVersionId(),
                version == null ? null : version.getVersionNumber(),
                version == null ? null : templateIdOf(version.getContent()),
                resume.getUpdatedAt());
    }

    private ResumeVersionVO toVersionVO(ResumeVersion version) {
        return new ResumeVersionVO(
                version.getId(),
                version.getVersionNumber(),
                version.getGenerationReason(),
                templateIdOf(version.getContent()),
                version.getContent(),
                version.getCreatedAt());
    }

    private static String templateIdOf(JsonNode content) {
        if (content == null) {
            return null;
        }
        JsonNode templateId = content.path("meta").path("templateId");
        return templateId.isMissingNode() || templateId.isNull() ? null : templateId.asText();
    }
}
