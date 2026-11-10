package com.team.resume.resume.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.dto.CreateResumeRequest;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.vo.ResumeDetailVO;
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

    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;
    private final ResumeVersionService versionService;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeMapper resumeMapper,
                         ResumeVersionMapper versionMapper,
                         ResumeVersionService versionService,
                         ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.versionMapper = versionMapper;
        this.versionService = versionService;
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
