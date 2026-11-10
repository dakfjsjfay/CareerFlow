package com.team.resume.profile.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.dto.ExperienceRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.support.ProfileVos;
import com.team.resume.profile.vo.ProfileVO;

/**
 * 工作/实习经历增改删（docs/api.md §6）。
 * 归属校验以当前用户的 user_profile 为准；新增/修改写 MANUAL + CONFIRMED 证据。
 */
@Service
public class ProfileExperienceService {

    private final ProfileExperienceMapper experienceMapper;
    private final ProfileEvidenceMapper evidenceMapper;
    private final ProfileDataService profileDataService;

    public ProfileExperienceService(ProfileExperienceMapper experienceMapper,
                                    ProfileEvidenceMapper evidenceMapper,
                                    ProfileDataService profileDataService) {
        this.experienceMapper = experienceMapper;
        this.evidenceMapper = evidenceMapper;
        this.profileDataService = profileDataService;
    }

    @Transactional
    public ProfileVO.ExperienceVO create(Long userId, ExperienceRequest request) {
        UserProfile profile = profileDataService.ensureProfile(userId);
        ProfileExperience experience = new ProfileExperience();
        experience.setProfileId(profile.getId());
        apply(experience, request);
        experienceMapper.insert(experience);
        recordEvidence(userId, experience);
        return ProfileVos.experience(experience);
    }

    @Transactional
    public ProfileVO.ExperienceVO update(Long userId, Long experienceId, ExperienceRequest request) {
        ProfileExperience experience = requireOwned(userId, experienceId);
        apply(experience, request);
        experienceMapper.updateById(experience);
        recordEvidence(userId, experience);
        return ProfileVos.experience(experience);
    }

    @Transactional
    public void delete(Long userId, Long experienceId) {
        ProfileExperience experience = requireOwned(userId, experienceId);
        experienceMapper.deleteById(experience.getId());
        evidenceMapper.delete(new LambdaQueryWrapper<ProfileEvidence>()
                .eq(ProfileEvidence::getUserId, userId)
                .eq(ProfileEvidence::getEntityType, ProfileEvidence.ENTITY_EXPERIENCE)
                .eq(ProfileEvidence::getEntityId, experience.getId()));
    }

    private ProfileExperience requireOwned(Long userId, Long experienceId) {
        UserProfile profile = profileDataService.findByUserId(userId);
        if (profile == null) {
            throw new BusinessException(404, "档案不存在");
        }
        ProfileExperience experience = experienceMapper.selectById(experienceId);
        if (experience == null) {
            throw new BusinessException(404, "工作/实习经历不存在");
        }
        if (!Objects.equals(experience.getProfileId(), profile.getId())) {
            throw new BusinessException(403, "无权操作该经历");
        }
        return experience;
    }

    private static void apply(ProfileExperience experience, ExperienceRequest request) {
        String company = request.getCompany() == null ? null : request.getCompany().trim();
        if (company == null || company.isEmpty()) {
            throw new BusinessException(400, "公司/单位不能为空");
        }
        experience.setCompany(company);
        experience.setPosition(normalizeText(request.getPosition()));
        experience.setDescription(normalizeText(request.getDescription()));
        experience.setAchievements(normalizeText(request.getAchievements()));
        experience.setStartDate(request.getStartDate());
        experience.setEndDate(request.getEndDate());
    }

    private void recordEvidence(Long userId, ProfileExperience experience) {
        ProfileEvidence evidence = new ProfileEvidence();
        evidence.setUserId(userId);
        evidence.setEntityType(ProfileEvidence.ENTITY_EXPERIENCE);
        evidence.setEntityId(experience.getId());
        evidence.setFieldName(ExtractionEvidenceFactory.FIELD_EXPERIENCE);
        evidence.setSourceType(ProfileEvidence.SOURCE_MANUAL);
        evidence.setSourceId(null);
        evidence.setEvidenceContent(toEvidenceJson(experience));
        evidence.setConfirmationStatus(ProfileEvidence.STATUS_CONFIRMED);
        evidenceMapper.insert(evidence);
    }

    private static String toEvidenceJson(ProfileExperience experience) {
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("company", experience.getCompany());
        if (experience.getPosition() != null) {
            data.put("position", experience.getPosition());
        }
        if (experience.getDescription() != null) {
            data.put("description", experience.getDescription());
        }
        if (experience.getAchievements() != null) {
            data.put("achievements", experience.getAchievements());
        }
        if (experience.getStartDate() != null) {
            data.put("startDate", experience.getStartDate().toString());
        }
        if (experience.getEndDate() != null) {
            data.put("endDate", experience.getEndDate().toString());
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
}
