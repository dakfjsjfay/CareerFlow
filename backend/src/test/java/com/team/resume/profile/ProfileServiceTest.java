package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team.resume.profile.dto.ProfileUpdateRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.service.ProfileDataService;
import com.team.resume.profile.service.ProfileService;
import com.team.resume.profile.vo.ProfileVO;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private ProfileProjectMapper projectMapper;

    @Mock
    private ProfileExperienceMapper experienceMapper;

    @Mock
    private ProfileEvidenceMapper evidenceMapper;

    @Mock
    private ProfileDataService profileDataService;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(userProfileMapper, projectMapper, experienceMapper,
                evidenceMapper, profileDataService);
    }

    @Test
    void getProfileReturnsEmptyStructureWhenNoProfileExists() {
        when(userProfileMapper.selectOne(any())).thenReturn(null);

        ProfileVO result = profileService.getProfile(1L);

        assertThat(result.id()).isNull();
        assertThat(result.skills()).isEmpty();
        assertThat(result.projects()).isEmpty();
        assertThat(result.experiences()).isEmpty();
        verifyNoInteractions(projectMapper, experienceMapper);
    }

    @Test
    void getProfileMapsConfirmedProfileProjectsAndExperiences() {
        UserProfile profile = profile();
        profile.setRealName("张三");
        profile.setSkills(List.of("Java"));
        ProfileProject project = project();
        ProfileExperience experience = experience();
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(projectMapper.selectList(any())).thenReturn(List.of(project));
        when(experienceMapper.selectList(any())).thenReturn(List.of(experience));

        ProfileVO result = profileService.getProfile(1L);

        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.realName()).isEqualTo("张三");
        assertThat(result.skills()).containsExactly("Java");
        assertThat(result.projects()).singleElement().satisfies(vo -> {
            assertThat(vo.projectName()).isEqualTo("学生管理系统");
            assertThat(vo.techStack()).containsExactly("Java");
            assertThat(vo.startDate()).isEqualTo(LocalDate.of(2025, 3, 1));
        });
        assertThat(result.experiences()).singleElement().satisfies(vo -> {
            assertThat(vo.company()).isEqualTo("某某科技");
            assertThat(vo.startDate()).isEqualTo(LocalDate.of(2025, 7, 1));
        });
    }

    @Test
    void updateProfileWritesManualEvidenceOnlyForChangedFields() {
        UserProfile profile = profile();
        profile.setRealName("张三");
        profile.setSkills(new ArrayList<>(List.of("Java")));
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(userProfileMapper.updateById(profile)).thenReturn(1);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(experienceMapper.selectList(any())).thenReturn(List.of());

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setRealName("张三");
        request.setPhone("13800000000");
        request.setSchool("某某大学");
        request.setSkills(List.of("Java", " java ", "MySQL"));

        ProfileVO result = profileService.updateProfile(1L, request);

        assertThat(result.phone()).isEqualTo("13800000000");
        assertThat(result.school()).isEqualTo("某某大学");
        assertThat(result.skills()).containsExactly("Java", "MySQL");
        verify(userProfileMapper).updateById(profile);

        ArgumentCaptor<ProfileEvidence> captor = ArgumentCaptor.forClass(ProfileEvidence.class);
        verify(evidenceMapper, times(3)).insert(captor.capture());
        List<ProfileEvidence> evidence = captor.getAllValues();
        assertThat(evidence).allSatisfy(e -> {
            assertThat(e.getUserId()).isEqualTo(1L);
            assertThat(e.getEntityId()).isEqualTo(9L);
            assertThat(e.getSourceType()).isEqualTo(ProfileEvidence.SOURCE_MANUAL);
            assertThat(e.getSourceId()).isNull();
            assertThat(e.getConfirmationStatus()).isEqualTo(ProfileEvidence.STATUS_CONFIRMED);
        });
        assertThat(evidence).extracting(ProfileEvidence::getFieldName)
                .containsExactly("phone", "school", "skills");
        assertThat(evidence).extracting(ProfileEvidence::getEntityType)
                .containsExactly(ProfileEvidence.ENTITY_PROFILE, ProfileEvidence.ENTITY_PROFILE,
                        ProfileEvidence.ENTITY_SKILL);
        assertThat(evidence.get(2).getEvidenceContent()).isEqualTo("Java、MySQL");
    }

    @Test
    void updateProfileClearsBlankFieldAndRecordsEmptyEvidence() {
        UserProfile profile = profile();
        profile.setRealName("张三");
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(userProfileMapper.updateById(profile)).thenReturn(1);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(experienceMapper.selectList(any())).thenReturn(List.of());

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setRealName("   ");

        ProfileVO result = profileService.updateProfile(1L, request);

        assertThat(result.realName()).isNull();
        ArgumentCaptor<ProfileEvidence> captor = ArgumentCaptor.forClass(ProfileEvidence.class);
        verify(evidenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getFieldName()).isEqualTo("real_name");
        assertThat(captor.getValue().getEvidenceContent()).isEmpty();
    }

    @Test
    void updateProfileDoesNotWriteWhenNothingChanged() {
        UserProfile profile = profile();
        profile.setRealName("张三");
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(experienceMapper.selectList(any())).thenReturn(List.of());

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setRealName("张三");

        profileService.updateProfile(1L, request);

        verifyNoInteractions(evidenceMapper);
        verify(userProfileMapper, never()).updateById(any(UserProfile.class));
    }

    private static UserProfile profile() {
        UserProfile profile = new UserProfile();
        profile.setId(9L);
        profile.setUserId(1L);
        return profile;
    }

    private static ProfileProject project() {
        ProfileProject project = new ProfileProject();
        project.setId(21L);
        project.setProfileId(9L);
        project.setProjectName("学生管理系统");
        project.setDescription("增删改查");
        project.setTechStack(List.of("Java"));
        project.setStartDate(LocalDate.of(2025, 3, 1));
        return project;
    }

    private static ProfileExperience experience() {
        ProfileExperience experience = new ProfileExperience();
        experience.setId(31L);
        experience.setProfileId(9L);
        experience.setCompany("某某科技");
        experience.setPosition("后端实习生");
        experience.setStartDate(LocalDate.of(2025, 7, 1));
        return experience;
    }
}
