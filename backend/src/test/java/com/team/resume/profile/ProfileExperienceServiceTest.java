package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.dto.ExperienceRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.service.ProfileDataService;
import com.team.resume.profile.service.ProfileExperienceService;
import com.team.resume.profile.vo.ProfileVO;

@ExtendWith(MockitoExtension.class)
class ProfileExperienceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProfileExperienceMapper experienceMapper;

    @Mock
    private ProfileEvidenceMapper evidenceMapper;

    @Mock
    private ProfileDataService profileDataService;

    private ProfileExperienceService experienceService;

    @BeforeEach
    void setUp() {
        experienceService = new ProfileExperienceService(
                experienceMapper, evidenceMapper, profileDataService);
    }

    @Test
    void createExperienceWritesManualConfirmedEvidence() throws Exception {
        when(profileDataService.ensureProfile(1L)).thenReturn(profile(9L));
        when(experienceMapper.insert(any(ProfileExperience.class))).thenAnswer(invocation -> {
            ProfileExperience experience = invocation.getArgument(0);
            experience.setId(31L);
            return 1;
        });

        ProfileVO.ExperienceVO result = experienceService.create(1L, request(" 某某科技 "));

        assertThat(result.id()).isEqualTo(31L);
        assertThat(result.company()).isEqualTo("某某科技");
        assertThat(result.position()).isEqualTo("后端实习生");

        ArgumentCaptor<ProfileEvidence> captor = ArgumentCaptor.forClass(ProfileEvidence.class);
        verify(evidenceMapper).insert(captor.capture());
        ProfileEvidence evidence = captor.getValue();
        assertThat(evidence.getEntityType()).isEqualTo(ProfileEvidence.ENTITY_EXPERIENCE);
        assertThat(evidence.getEntityId()).isEqualTo(31L);
        assertThat(evidence.getFieldName()).isEqualTo("experience");
        assertThat(evidence.getSourceType()).isEqualTo(ProfileEvidence.SOURCE_MANUAL);
        assertThat(evidence.getConfirmationStatus()).isEqualTo(ProfileEvidence.STATUS_CONFIRMED);
        JsonNode data = objectMapper.readTree(evidence.getEvidenceContent());
        assertThat(data.path("company").asText()).isEqualTo("某某科技");
        assertThat(data.path("position").asText()).isEqualTo("后端实习生");
    }

    @Test
    void updateRejectsAnotherUsersExperience() {
        when(profileDataService.findByUserId(1L)).thenReturn(profile(9L));
        when(experienceMapper.selectById(31L)).thenReturn(experience(31L, 99L));

        assertThatThrownBy(() -> experienceService.update(1L, 31L, request("新公司")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(experienceMapper, never()).updateById(any(ProfileExperience.class));
        verifyNoInteractions(evidenceMapper);
    }

    @Test
    void updateExperienceWritesANewManualEvidenceRow() {
        ProfileExperience experience = experience(31L, 9L);
        when(profileDataService.findByUserId(1L)).thenReturn(profile(9L));
        when(experienceMapper.selectById(31L)).thenReturn(experience);
        when(experienceMapper.updateById(experience)).thenReturn(1);

        ProfileVO.ExperienceVO result = experienceService.update(1L, 31L, request("新公司"));

        assertThat(result.company()).isEqualTo("新公司");
        verify(evidenceMapper).insert(any(ProfileEvidence.class));
    }

    @Test
    void deleteExperienceRemovesEntityAndItsEvidence() {
        when(profileDataService.findByUserId(1L)).thenReturn(profile(9L));
        when(experienceMapper.selectById(31L)).thenReturn(experience(31L, 9L));

        experienceService.delete(1L, 31L);

        verify(experienceMapper).deleteById(31L);
        verify(evidenceMapper).delete(any());
    }

    @Test
    void missingExperienceReturns404() {
        when(profileDataService.findByUserId(1L)).thenReturn(profile(9L));
        when(experienceMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> experienceService.update(1L, 99L, request("新公司")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    private static ExperienceRequest request(String company) {
        ExperienceRequest request = new ExperienceRequest();
        request.setCompany(company);
        request.setPosition("后端实习生");
        request.setDescription("参与接口开发");
        request.setAchievements("完成 3 个接口");
        request.setStartDate(LocalDate.of(2025, 7, 1));
        request.setEndDate(LocalDate.of(2025, 9, 1));
        return request;
    }

    private static UserProfile profile(Long id) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUserId(1L);
        return profile;
    }

    private static ProfileExperience experience(Long id, Long profileId) {
        ProfileExperience experience = new ProfileExperience();
        experience.setId(id);
        experience.setProfileId(profileId);
        experience.setCompany("某某科技");
        return experience;
    }
}
