package com.team.resume.profile.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.dto.ProjectRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.support.ProfileVos;
import com.team.resume.profile.vo.ProfileVO;

/**
 * 项目经历增改删（docs/api.md §6）。
 * 归属校验以当前用户的 user_profile 为准；新增/修改写 MANUAL + CONFIRMED 证据。
 */
@Service
public class ProfileProjectService {

    private final ProfileProjectMapper projectMapper;
    private final ProfileEvidenceMapper evidenceMapper;
    private final ProfileDataService profileDataService;

    public ProfileProjectService(ProfileProjectMapper projectMapper,
                                 ProfileEvidenceMapper evidenceMapper,
                                 ProfileDataService profileDataService) {
        this.projectMapper = projectMapper;
        this.evidenceMapper = evidenceMapper;
        this.profileDataService = profileDataService;
    }

    @Transactional
    public ProfileVO.ProjectVO create(Long userId, ProjectRequest request) {
        UserProfile profile = profileDataService.ensureProfile(userId);
        ProfileProject project = new ProfileProject();
        project.setProfileId(profile.getId());
        apply(project, request);
        projectMapper.insert(project);
        recordEvidence(userId, project);
        return ProfileVos.project(project);
    }

    @Transactional
    public ProfileVO.ProjectVO update(Long userId, Long projectId, ProjectRequest request) {
        ProfileProject project = requireOwned(userId, projectId);
        apply(project, request);
        projectMapper.updateById(project);
        recordEvidence(userId, project);
        return ProfileVos.project(project);
    }

    @Transactional
    public void delete(Long userId, Long projectId) {
        ProfileProject project = requireOwned(userId, projectId);
        projectMapper.deleteById(project.getId());
        evidenceMapper.delete(new LambdaQueryWrapper<ProfileEvidence>()
                .eq(ProfileEvidence::getUserId, userId)
                .eq(ProfileEvidence::getEntityType, ProfileEvidence.ENTITY_PROJECT)
                .eq(ProfileEvidence::getEntityId, project.getId()));
    }

    private ProfileProject requireOwned(Long userId, Long projectId) {
        UserProfile profile = profileDataService.findByUserId(userId);
        if (profile == null) {
            throw new BusinessException(404, "档案不存在");
        }
        ProfileProject project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (!Objects.equals(project.getProfileId(), profile.getId())) {
            throw new BusinessException(403, "无权操作该项目");
        }
        return project;
    }

    private static void apply(ProfileProject project, ProjectRequest request) {
        String name = request.getProjectName() == null ? null : request.getProjectName().trim();
        if (name == null || name.isEmpty()) {
            throw new BusinessException(400, "项目名称不能为空");
        }
        project.setProjectName(name);
        project.setRole(normalizeText(request.getRole()));
        project.setDescription(normalizeText(request.getDescription()));
        project.setTechStack(normalizeList(request.getTechStack()));
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
    }

    private void recordEvidence(Long userId, ProfileProject project) {
        ProfileEvidence evidence = new ProfileEvidence();
        evidence.setUserId(userId);
        evidence.setEntityType(ProfileEvidence.ENTITY_PROJECT);
        evidence.setEntityId(project.getId());
        evidence.setFieldName(ExtractionEvidenceFactory.FIELD_PROJECT);
        evidence.setSourceType(ProfileEvidence.SOURCE_MANUAL);
        evidence.setSourceId(null);
        evidence.setEvidenceContent(toEvidenceJson(project));
        evidence.setConfirmationStatus(ProfileEvidence.STATUS_CONFIRMED);
        evidenceMapper.insert(evidence);
    }

    private static String toEvidenceJson(ProfileProject project) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("projectName", project.getProjectName());
        if (project.getRole() != null) {
            data.put("role", project.getRole());
        }
        if (project.getDescription() != null) {
            data.put("description", project.getDescription());
        }
        if (project.getTechStack() != null && !project.getTechStack().isEmpty()) {
            ArrayNode array = data.putArray("techStack");
            project.getTechStack().forEach(array::add);
        }
        if (project.getStartDate() != null) {
            data.put("startDate", project.getStartDate().toString());
        }
        if (project.getEndDate() != null) {
            data.put("endDate", project.getEndDate().toString());
        }
        return data.toString();
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> normalizeList(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String text = value.trim();
            if (text.isEmpty()) {
                continue;
            }
            if (seen.add(text.toLowerCase(Locale.ROOT))) {
                result.add(text);
            }
        }
        return result;
    }
}
