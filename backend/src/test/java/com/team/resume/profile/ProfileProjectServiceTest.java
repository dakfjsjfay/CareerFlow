package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.dto.ProjectRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.service.ProfileDataService;
import com.team.resume.profile.service.ProfileProjectService;
import com.team.resume.profile.vo.ProfileVO;

@ExtendWith(MockitoExtension.class)
class ProfileProjectServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProfileProjectMapper projectMapper;

    @Mock
    private ProfileEvidenceMapper evidenceMapper;

    @Mock
    private ProfileDataService profileDataService;

    private ProfileProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProfileProjectService(projectMapper, evidenceMapper, profileDataService);
    }

    @Test
    void createProjectWritesManualConfirmedEvidence() throws Exception {
        when(profileDataService.ensureProfile(1L)).thenReturn(profile(9L));
        when(projectMapper.insert(any(ProfileProject.class))).thenAnswer(invocation -> {
            ProfileProject project = invocation.getArgument(0);
            project.setId(21L);
            return 1;
        });

        ProfileVO.ProjectVO result = projectService.create(1L, request(" 学生管理系统 "));

        assertThat(result.id()).isEqualTo(21L);
        assertThat(result.projectName()).isEqualTo("学生管理系统");
        assertThat(result.techStack()).containsExactly("Java", "MySQL");

        ArgumentCaptor<ProfileEvidence> captor = ArgumentCaptor.forClass(ProfileEvidence.class);
        verify(evidenceMapper).insert(captor.capture());
        ProfileEvidence evidence = captor.getValue();
        assertThat(evidence.getUserId()).isEqualTo(1L);
        assertThat(evidence.getEntityType()).isEqualTo(ProfileEvidence.ENTITY_PROJECT);
        assertThat(evidence.getEntityId()).isEqualTo(21L);
        assertThat(evidence.getFieldName()).isEqualTo("project");
        assertThat(evidence.getSourceType()).isEqualTo(ProfileEvidence.SOURCE_MANUAL);
        assertThat(evidence.getConfirmationStatus()).isEqualTo(ProfileEvidence.STATUS_CONFIRMED);
        JsonNode data = objectMapper.readTree(evidence.getEvidenceContent());
        assertThat(data.path("projectName").asText()).isEqualTo("学生管理系统");
        assertThat(data.path("techStack").size()).isEqualTo(2);
    }

    @Test
    void updateRejectsAnotherUsersProject() {
        when(profileDataService.findByUserId(1L)).thenReturn(profile(9L));
        when(projectMapper.selectById(21L)).thenReturn(project(21L, 99L));

        assertThatThrownBy(() -> projectService.update(1L, 21L, request("新项目")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verify(projectMapper, never()).updateById(any(ProfileProject.class));
        verifyNoInteractions(evidenceMapper);
    }

    @Test
    void updateProjectWritesANewManualEvidenceRow() {
        UserProfile profile = profile(9L);
        ProfileProject project = project(21L, 9L);
        when(profileDataService.findByUserId(1L)).thenReturn(profile);
        when(projectMapper.selectById(21L)).thenReturn(project);
        when(projectMapper.updateById(project)).thenReturn(1);

        ProfileVO.ProjectVO result = projectService.update(1L, 21L, request("新项目"));

        assertThat(result.projectName()).isEqualTo("新项目");
        assertThat(project.getProjectName()).isEqualTo("新项目");
        verify(evidenceMapper).insert(any(ProfileEvidence.class));
    }

    @Test
    void deleteProjectRemovesEntityAndItsEvidence() {
        when(profileDataService.findByUserId(1L)).thenReturn(profile(9L));
        when(projectMapper.selectById(21L)).thenReturn(project(21L, 9L));

        projectService.delete(1L, 21L);

        verify(projectMapper).deleteById(21L);
        verify(evidenceMapper).delete(any());
    }

    @Test
    void missingProjectReturns404() {
        when(profileDataService.findByUserId(1L)).thenReturn(profile(9L));
        when(projectMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> projectService.update(1L, 99L, request("新项目")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
    }

    private static ProjectRequest request(String name) {
        ProjectRequest request = new ProjectRequest();
        request.setProjectName(name);
        request.setRole("后端开发");
        request.setDescription("增删改查");
        request.setTechStack(List.of("Java", " MySQL "));
        request.setStartDate(LocalDate.of(2025, 3, 1));
        request.setEndDate(LocalDate.of(2025, 6, 1));
        return request;
    }

    private static UserProfile profile(Long id) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUserId(1L);
        return profile;
    }

    private static ProfileProject project(Long id, Long profileId) {
        ProfileProject project = new ProfileProject();
        project.setId(id);
        project.setProfileId(profileId);
        project.setProjectName("学生管理系统");
        return project;
    }
}
