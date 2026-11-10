package com.team.resume.resume.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;
import com.team.resume.resume.vo.ResumeContent;
import com.team.resume.resume.vo.ResumeVersionSummaryVO;
import com.team.resume.resume.vo.ResumeVersionVO;

/**
 * 版本写入与读取（docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md §8.5）。
 * <p>
 * 生成新版本时锁定 resume 行，版本号 = max + 1；旧版本没有任何 update/delete 入口。
 */
@Service
public class ResumeVersionService {

    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public ResumeVersionService(ResumeMapper resumeMapper,
                                ResumeVersionMapper versionMapper,
                                ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResumeVersion create(Long userId, Long resumeId, ResumeContent content,
                                String generationReason) {
        Resume resume = resumeMapper.selectByIdForUpdate(resumeId);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        if (!Objects.equals(resume.getUserId(), userId)) {
            throw new BusinessException(403, "无权操作该简历");
        }

        int nextNumber = versionMapper.selectMaxVersionNumber(resumeId) + 1;
        ResumeVersion version = new ResumeVersion();
        version.setResumeId(resumeId);
        version.setVersionNumber(nextNumber);
        version.setContent(objectMapper.valueToTree(content));
        version.setGenerationReason(generationReason);
        version.setCreatedAt(LocalDateTime.now());

        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "版本冲突，请重试");
        }

        resume.setCurrentVersionId(version.getId());
        resumeMapper.updateById(resume);
        return version;
    }

    public List<ResumeVersionSummaryVO> list(Long userId, Long resumeId) {
        requireResume(userId, resumeId);
        List<ResumeVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, resumeId)
                        .orderByDesc(ResumeVersion::getVersionNumber));
        return versions.stream().map(this::toSummary).toList();
    }

    public ResumeVersionVO get(Long userId, Long resumeId, Long versionId) {
        return toVO(requireOwned(userId, resumeId, versionId));
    }

    public ResumeVersion requireOwned(Long userId, Long resumeId, Long versionId) {
        requireResume(userId, resumeId);
        ResumeVersion version = versionMapper.selectById(versionId);
        if (version == null || !Objects.equals(version.getResumeId(), resumeId)) {
            throw new BusinessException(404, "简历版本不存在");
        }
        return version;
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

    private ResumeVersionSummaryVO toSummary(ResumeVersion version) {
        return new ResumeVersionSummaryVO(
                version.getId(),
                version.getVersionNumber(),
                version.getGenerationReason(),
                templateIdOf(version.getContent()),
                version.getCreatedAt());
    }

    private ResumeVersionVO toVO(ResumeVersion version) {
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
