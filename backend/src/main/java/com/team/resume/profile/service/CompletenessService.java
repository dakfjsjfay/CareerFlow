package com.team.resume.profile.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.vo.CompletenessVO;

/**
 * 完整度查询：加载用户已确认档案与证据标记，交给 CompletenessCalculator 计算。
 */
@Service
public class CompletenessService {

    private final UserProfileMapper userProfileMapper;
    private final ProfileProjectMapper projectMapper;
    private final ProfileExperienceMapper experienceMapper;
    private final ProfileEvidenceMapper evidenceMapper;
    private final CompletenessCalculator calculator;

    public CompletenessService(UserProfileMapper userProfileMapper,
                               ProfileProjectMapper projectMapper,
                               ProfileExperienceMapper experienceMapper,
                               ProfileEvidenceMapper evidenceMapper,
                               CompletenessCalculator calculator) {
        this.userProfileMapper = userProfileMapper;
        this.projectMapper = projectMapper;
        this.experienceMapper = experienceMapper;
        this.evidenceMapper = evidenceMapper;
        this.calculator = calculator;
    }

    public CompletenessVO get(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (profile == null) {
            return calculator.calculate(null, List.of(), List.of(), false, false);
        }

        List<ProfileProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<ProfileProject>()
                        .eq(ProfileProject::getProfileId, profile.getId())
                        .orderByAsc(ProfileProject::getId));
        List<ProfileExperience> experiences = experienceMapper.selectList(
                new LambdaQueryWrapper<ProfileExperience>()
                        .eq(ProfileExperience::getProfileId, profile.getId())
                        .orderByAsc(ProfileExperience::getId));

        Long confirmedSkills = evidenceMapper.selectCount(new LambdaQueryWrapper<ProfileEvidence>()
                .eq(ProfileEvidence::getUserId, userId)
                .eq(ProfileEvidence::getEntityType, ProfileEvidence.ENTITY_SKILL)
                .eq(ProfileEvidence::getConfirmationStatus, ProfileEvidence.STATUS_CONFIRMED));
        Long noExperience = evidenceMapper.selectCount(new LambdaQueryWrapper<ProfileEvidence>()
                .eq(ProfileEvidence::getUserId, userId)
                .eq(ProfileEvidence::getFieldName, ExtractionEvidenceFactory.FIELD_NO_EXPERIENCE)
                .eq(ProfileEvidence::getConfirmationStatus, ProfileEvidence.STATUS_CONFIRMED));

        boolean hasConfirmedSkillEvidence = confirmedSkills != null && confirmedSkills > 0;
        boolean hasConfirmedNoExperience = noExperience != null && noExperience > 0;
        return calculator.calculate(profile, projects, experiences,
                hasConfirmedSkillEvidence, hasConfirmedNoExperience);
    }
}
