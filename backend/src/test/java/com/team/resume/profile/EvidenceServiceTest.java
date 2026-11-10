package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team.resume.chat.entity.ChatMessage;
import com.team.resume.chat.mapper.ChatMessageMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.dto.ConfirmEvidenceRequest;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.service.EvidenceService;
import com.team.resume.profile.service.EvidenceSource;
import com.team.resume.profile.service.ExtractionEvidenceFactory;
import com.team.resume.profile.service.ProfileDataService;
import com.team.resume.profile.vo.ConfirmEvidenceResult;
import com.team.resume.profile.vo.EvidenceVO;

@ExtendWith(MockitoExtension.class)
class EvidenceServiceTest {

    @Mock
    private ProfileEvidenceMapper evidenceMapper;

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    @Mock
    private ExtractionEvidenceFactory extractionFactory;

    @Mock
    private ProfileDataService profileDataService;

    private EvidenceService evidenceService;

    @BeforeEach
    void setUp() {
        evidenceService = new EvidenceService(evidenceMapper, userProfileMapper,
                chatMessageMapper, extractionFactory, profileDataService);
    }

    @Test
    void listDefaultsToPendingAndAttachesOriginalChatContent() {
        ProfileEvidence row = evidence(101L, ProfileEvidence.ENTITY_SKILL, "skills", "Java");
        ChatMessage message = new ChatMessage();
        message.setId(50L);
        message.setContent("我学过 Java");
        when(evidenceMapper.selectList(any())).thenReturn(List.of(row));
        when(chatMessageMapper.selectBatchIds(anyCollection())).thenReturn(List.of(message));

        List<EvidenceVO> result = evidenceService.list(1L, null);

        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.id()).isEqualTo(101L);
            assertThat(vo.confirmationStatus()).isEqualTo(ProfileEvidence.STATUS_PENDING);
            assertThat(vo.sourceContent()).isEqualTo("我学过 Java");
            assertThat(vo.evidenceContent()).isEqualTo("Java");
        });
    }

    @Test
    void listManualEvidenceWithoutChatSourceDoesNotFail() {
        ProfileEvidence row = evidence(101L, ProfileEvidence.ENTITY_PROFILE, "real_name", "张三");
        row.setSourceType(ProfileEvidence.SOURCE_MANUAL);
        row.setSourceId(null);
        row.setConfirmationStatus(ProfileEvidence.STATUS_CONFIRMED);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(row));

        List<EvidenceVO> result = evidenceService.list(1L, "CONFIRMED");

        assertThat(result).singleElement().satisfies(vo -> {
            assertThat(vo.sourceType()).isEqualTo(ProfileEvidence.SOURCE_MANUAL);
            assertThat(vo.sourceContent()).isNull();
        });
    }

    @Test
    void listRejectsUnknownStatus() {
        assertThatThrownBy(() -> evidenceService.list(1L, "DONE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
        verifyNoInteractions(evidenceMapper);
    }

    @Test
    void confirmMissingEvidenceReturns404() {
        when(evidenceMapper.selectById(101L)).thenReturn(null);

        assertThatThrownBy(() -> evidenceService.confirm(1L, request(List.of(101L), true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);
        verifyNoInteractions(profileDataService);
    }

    @Test
    void confirmOtherUsersEvidenceReturns403() {
        ProfileEvidence row = evidence(101L, ProfileEvidence.ENTITY_SKILL, "skills", "Java");
        row.setUserId(2L);
        when(evidenceMapper.selectById(101L)).thenReturn(row);

        assertThatThrownBy(() -> evidenceService.confirm(1L, request(List.of(101L), true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(403);
        verifyNoInteractions(profileDataService);
    }

    @Test
    void confirmAlreadyProcessedEvidenceReturns409WithCurrentStatus() {
        ProfileEvidence row = evidence(101L, ProfileEvidence.ENTITY_SKILL, "skills", "Java");
        row.setConfirmationStatus(ProfileEvidence.STATUS_CONFIRMED);
        when(evidenceMapper.selectById(101L)).thenReturn(row);

        assertThatThrownBy(() -> evidenceService.confirm(1L, request(List.of(101L), true)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CONFIRMED")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
        verifyNoInteractions(profileDataService);
    }

    @Test
    void confirmSkillWritesProfileAndBackfillsEntityId() {
        ProfileEvidence row = evidence(101L, ProfileEvidence.ENTITY_SKILL, "skills", "Java");
        UserProfile profile = profile(9L);
        when(evidenceMapper.selectById(101L)).thenReturn(row);
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(profileDataService.addSkill(profile, "Java")).thenReturn(true);
        when(evidenceMapper.markProcessed(101L, 1L, ProfileEvidence.STATUS_CONFIRMED, 9L))
                .thenReturn(1);

        ConfirmEvidenceResult result = evidenceService.confirm(1L, request(List.of(101L), true));

        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(result.updatedFields()).containsExactly("skills");
        verify(userProfileMapper).updateById(profile);
    }

    @Test
    void confirmProjectCreatesEntityAndBackfillsEntityId() {
        String content = "{\"projectName\":\"学生管理系统\",\"techStack\":[\"Java\"]}";
        ProfileEvidence row = evidence(102L, ProfileEvidence.ENTITY_PROJECT, "project", content);
        UserProfile profile = profile(9L);
        ProfileProject project = new ProfileProject();
        project.setId(77L);
        project.setProjectName("学生管理系统");
        project.setTechStack(List.of("Java"));
        when(evidenceMapper.selectById(102L)).thenReturn(row);
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(profileDataService.createProject(9L, content)).thenReturn(project);
        when(evidenceMapper.markProcessed(102L, 1L, ProfileEvidence.STATUS_CONFIRMED, 77L))
                .thenReturn(1);

        ConfirmEvidenceResult result = evidenceService.confirm(1L, request(List.of(102L), true));

        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(result.updatedFields())
                .containsExactly("project.projectName", "project.techStack");
        // 项目是独立实体，不需要更新 user_profile
        verify(userProfileMapper, never()).updateById(any(UserProfile.class));
    }

    @Test
    void rejectOnlyUpdatesStatusWithoutWritingProfile() {
        ProfileEvidence row = evidence(103L, ProfileEvidence.ENTITY_PROFILE, "major", "计算机");
        when(evidenceMapper.selectById(103L)).thenReturn(row);
        when(evidenceMapper.markProcessed(103L, 1L, ProfileEvidence.STATUS_REJECTED, null))
                .thenReturn(1);

        ConfirmEvidenceResult result = evidenceService.confirm(1L, request(List.of(103L), false));

        assertThat(result.confirmedCount()).isZero();
        assertThat(result.updatedFields()).isEmpty();
        verifyNoInteractions(profileDataService, userProfileMapper);
    }

    @Test
    void duplicateIdsInRequestAreProcessedOnceAndUpdatedFieldsUseCamelCase() {
        ProfileEvidence row = evidence(
                104L, ProfileEvidence.ENTITY_PROFILE, "target_position", "Java 后端开发");
        UserProfile profile = profile(9L);
        when(evidenceMapper.selectById(104L)).thenReturn(row);
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(profileDataService.applyProfileField(
                profile, "target_position", "Java 后端开发")).thenReturn(true);
        when(evidenceMapper.markProcessed(104L, 1L, ProfileEvidence.STATUS_CONFIRMED, 9L))
                .thenReturn(1);

        ConfirmEvidenceResult result = evidenceService.confirm(
                1L, request(List.of(104L, 104L), true));

        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(result.updatedFields()).containsExactly("targetPosition");
        verify(evidenceMapper, times(1)).selectById(104L);
        verify(evidenceMapper, times(1)).markProcessed(
                104L, 1L, ProfileEvidence.STATUS_CONFIRMED, 9L);
    }

    @Test
    void confirmNoExperienceEvidenceMarksItConfirmedWithoutWritingProfileField() {
        ProfileEvidence row = evidence(106L, ProfileEvidence.ENTITY_PROFILE,
                ExtractionEvidenceFactory.FIELD_NO_EXPERIENCE, "无工作经历");
        UserProfile profile = profile(9L);
        when(evidenceMapper.selectById(106L)).thenReturn(row);
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(evidenceMapper.markProcessed(106L, 1L, ProfileEvidence.STATUS_CONFIRMED, 9L))
                .thenReturn(1);

        ConfirmEvidenceResult result = evidenceService.confirm(1L, request(List.of(106L), true));

        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(result.updatedFields()).containsExactly("noExperience");
        verify(profileDataService, never()).applyProfileField(any(), any(), any());
        verify(userProfileMapper, never()).updateById(any(UserProfile.class));
    }

    @Test
    void concurrentConfirmReturns409WhenConditionalUpdateLoses() {
        ProfileEvidence row = evidence(105L, ProfileEvidence.ENTITY_PROFILE, "major", "计算机");
        UserProfile profile = profile(9L);
        when(evidenceMapper.selectById(105L)).thenReturn(row);
        when(profileDataService.ensureProfile(1L)).thenReturn(profile);
        when(profileDataService.applyProfileField(profile, "major", "计算机")).thenReturn(true);
        when(evidenceMapper.markProcessed(105L, 1L, ProfileEvidence.STATUS_CONFIRMED, 9L))
                .thenReturn(0);

        assertThatThrownBy(() -> evidenceService.confirm(1L, request(List.of(105L), true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);
    }

    @Test
    void createPendingSkipsAlreadyPendingOrConfirmedDuplicates() {
        ProfileEvidence pending = evidence(10L, ProfileEvidence.ENTITY_SKILL, "skills", "Java");
        ProfileEvidence confirmed = evidence(11L, ProfileEvidence.ENTITY_SKILL, "skills", "MySQL");
        confirmed.setConfirmationStatus(ProfileEvidence.STATUS_CONFIRMED);
        ProfileEvidence sameAsPending = evidence(null, ProfileEvidence.ENTITY_SKILL, "skills", "Java");
        ProfileEvidence sameAsConfirmed = evidence(null, ProfileEvidence.ENTITY_SKILL, "skills", "MySQL");
        ProfileEvidence fresh = evidence(null, ProfileEvidence.ENTITY_SKILL, "skills", "Redis");
        List<EvidenceSource> sources = List.of(new EvidenceSource(55L, "我学过 Java、MySQL 和 Redis"));
        when(extractionFactory.build(1L, sources, null))
                .thenReturn(List.of(sameAsPending, sameAsConfirmed, fresh));
        when(evidenceMapper.selectList(any())).thenReturn(List.of(pending, confirmed));

        evidenceService.createPendingFromExtraction(1L, sources, null);

        ArgumentCaptor<ProfileEvidence> captor = ArgumentCaptor.forClass(ProfileEvidence.class);
        verify(evidenceMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getEvidenceContent()).isEqualTo("Redis");
    }

    private static ProfileEvidence evidence(Long id, String entityType, String fieldName,
                                            String content) {
        ProfileEvidence evidence = new ProfileEvidence();
        evidence.setId(id);
        evidence.setUserId(1L);
        evidence.setEntityType(entityType);
        evidence.setFieldName(fieldName);
        evidence.setEvidenceContent(content);
        evidence.setSourceType(ProfileEvidence.SOURCE_CHAT);
        evidence.setSourceId(50L);
        evidence.setConfirmationStatus(ProfileEvidence.STATUS_PENDING);
        return evidence;
    }

    private static UserProfile profile(Long id) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUserId(1L);
        return profile;
    }

    private static ConfirmEvidenceRequest request(List<Long> ids, boolean confirmed) {
        ConfirmEvidenceRequest request = new ConfirmEvidenceRequest();
        request.setEvidenceIds(ids);
        request.setConfirmed(confirmed);
        return request;
    }
}
