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
import com.team.resume.profile.dto.ProfileUpdateRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.support.ProfileVos;
import com.team.resume.profile.vo.ProfileVO;

/**
 * 档案读写（docs/api.md §6）：
 * - 读取只返回已确认的 user_profile / 项目 / 经历；
 * - 手动编辑的每个变更字段都写一条 MANUAL + CONFIRMED 证据，保证手动数据同样可追溯。
 */
@Service
public class ProfileService {

    private final UserProfileMapper userProfileMapper;
    private final ProfileProjectMapper projectMapper;
    private final ProfileExperienceMapper experienceMapper;
    private final ProfileEvidenceMapper evidenceMapper;
    private final ProfileDataService profileDataService;

    public ProfileService(UserProfileMapper userProfileMapper,
                          ProfileProjectMapper projectMapper,
                          ProfileExperienceMapper experienceMapper,
                          ProfileEvidenceMapper evidenceMapper,
                          ProfileDataService profileDataService) {
        this.userProfileMapper = userProfileMapper;
        this.projectMapper = projectMapper;
        this.experienceMapper = experienceMapper;
        this.evidenceMapper = evidenceMapper;
        this.profileDataService = profileDataService;
    }

    public ProfileVO getProfile(Long userId) {
        UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
        if (profile == null) {
            return emptyProfile();
        }
        List<ProfileProject> projects = projectMapper.selectList(
                new LambdaQueryWrapper<ProfileProject>()
                        .eq(ProfileProject::getProfileId, profile.getId())
                        .orderByAsc(ProfileProject::getId));
        List<ProfileExperience> experiences = experienceMapper.selectList(
                new LambdaQueryWrapper<ProfileExperience>()
                        .eq(ProfileExperience::getProfileId, profile.getId())
                        .orderByAsc(ProfileExperience::getId));
        return toVO(profile, projects, experiences);
    }

    @Transactional
    public ProfileVO updateProfile(Long userId, ProfileUpdateRequest request) {
        UserProfile profile = profileDataService.ensureProfile(userId);
        boolean dirty = false;

        if (request.getRealName() != null) {
            String value = normalizeText(request.getRealName());
            if (!Objects.equals(profile.getRealName(), value)) {
                profile.setRealName(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_REAL_NAME, value);
                dirty = true;
            }
        }
        if (request.getPhone() != null) {
            String value = normalizeText(request.getPhone());
            if (!Objects.equals(profile.getPhone(), value)) {
                profile.setPhone(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_PHONE, value);
                dirty = true;
            }
        }
        if (request.getSchool() != null) {
            String value = normalizeText(request.getSchool());
            if (!Objects.equals(profile.getSchool(), value)) {
                profile.setSchool(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_SCHOOL, value);
                dirty = true;
            }
        }
        if (request.getMajor() != null) {
            String value = normalizeText(request.getMajor());
            if (!Objects.equals(profile.getMajor(), value)) {
                profile.setMajor(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_MAJOR, value);
                dirty = true;
            }
        }
        if (request.getDegree() != null) {
            String value = normalizeText(request.getDegree());
            if (!Objects.equals(profile.getDegree(), value)) {
                profile.setDegree(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_DEGREE, value);
                dirty = true;
            }
        }
        if (request.getGraduationYear() != null
                && !Objects.equals(profile.getGraduationYear(), request.getGraduationYear())) {
            profile.setGraduationYear(request.getGraduationYear());
            recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                    ExtractionEvidenceFactory.FIELD_GRADUATION_YEAR,
                    String.valueOf(request.getGraduationYear()));
            dirty = true;
        }
        if (request.getSkills() != null) {
            List<String> skills = normalizeSkills(request.getSkills());
            List<String> current = profile.getSkills() == null ? List.of() : profile.getSkills();
            if (!Objects.equals(current, skills)) {
                profile.setSkills(new ArrayList<>(skills));
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_SKILL,
                        ExtractionEvidenceFactory.FIELD_SKILLS, String.join("、", skills));
                dirty = true;
            }
        }
        if (request.getTargetPosition() != null) {
            String value = normalizeText(request.getTargetPosition());
            if (!Objects.equals(profile.getTargetPosition(), value)) {
                profile.setTargetPosition(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_TARGET_POSITION, value);
                dirty = true;
            }
        }
        if (request.getTargetCity() != null) {
            String value = normalizeText(request.getTargetCity());
            if (!Objects.equals(profile.getTargetCity(), value)) {
                profile.setTargetCity(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_TARGET_CITY, value);
                dirty = true;
            }
        }
        if (request.getExpectedSalary() != null) {
            String value = normalizeText(request.getExpectedSalary());
            if (!Objects.equals(profile.getExpectedSalary(), value)) {
                profile.setExpectedSalary(value);
                recordManualEvidence(userId, profile.getId(), ProfileEvidence.ENTITY_PROFILE,
                        ExtractionEvidenceFactory.FIELD_EXPECTED_SALARY, value);
                dirty = true;
            }
        }

        if (dirty) {
            userProfileMapper.updateById(profile);
        }
        return getProfile(userId);
    }

    private void recordManualEvidence(Long userId, Long profileId, String entityType,
                                      String fieldName, String content) {
        ProfileEvidence evidence = new ProfileEvidence();
        evidence.setUserId(userId);
        evidence.setEntityType(entityType);
        evidence.setEntityId(profileId);
        evidence.setFieldName(fieldName);
        evidence.setSourceType(ProfileEvidence.SOURCE_MANUAL);
        evidence.setSourceId(null);
        evidence.setEvidenceContent(content == null ? "" : content);
        evidence.setConfirmationStatus(ProfileEvidence.STATUS_CONFIRMED);
        evidenceMapper.insert(evidence);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private static List<String> normalizeSkills(List<String> skills) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String skill : skills) {
            if (skill == null) {
                continue;
            }
            String text = skill.trim();
            if (text.isEmpty()) {
                continue;
            }
            if (seen.add(text.toLowerCase(Locale.ROOT))) {
                result.add(text);
            }
        }
        return result;
    }

    private static ProfileVO emptyProfile() {
        return new ProfileVO(null, null, null, null, null, null, null,
                List.of(), null, null, null, List.of(), List.of());
    }

    private static ProfileVO toVO(UserProfile profile, List<ProfileProject> projects,
                                  List<ProfileExperience> experiences) {
        List<ProfileProject> safeProjects = projects == null ? List.of() : projects;
        List<ProfileExperience> safeExperiences = experiences == null ? List.of() : experiences;
        return new ProfileVO(
                profile.getId(),
                profile.getRealName(),
                profile.getPhone(),
                profile.getSchool(),
                profile.getMajor(),
                profile.getDegree(),
                profile.getGraduationYear(),
                profile.getSkills() == null ? List.of() : List.copyOf(profile.getSkills()),
                profile.getTargetPosition(),
                profile.getTargetCity(),
                profile.getExpectedSalary(),
                safeProjects.stream().map(ProfileVos::project).toList(),
                safeExperiences.stream().map(ProfileVos::experience).toList());
    }
}
