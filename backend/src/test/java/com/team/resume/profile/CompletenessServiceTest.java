package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.service.CompletenessCalculator;
import com.team.resume.profile.service.CompletenessService;
import com.team.resume.profile.vo.CompletenessVO;

@ExtendWith(MockitoExtension.class)
class CompletenessServiceTest {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private ProfileProjectMapper projectMapper;

    @Mock
    private ProfileExperienceMapper experienceMapper;

    @Mock
    private ProfileEvidenceMapper evidenceMapper;

    @Mock
    private CompletenessCalculator calculator;

    private CompletenessService completenessService;

    @BeforeEach
    void setUp() {
        completenessService = new CompletenessService(
                userProfileMapper, projectMapper, experienceMapper, evidenceMapper, calculator);
    }

    @Test
    void loadsProfileEntriesAndConfirmedEvidenceFlags() {
        UserProfile profile = profile();
        List<ProfileProject> projects = List.of(new ProfileProject());
        List<ProfileExperience> experiences = List.of(new ProfileExperience());
        CompletenessVO expected = new CompletenessVO(80, List.of());
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(projectMapper.selectList(any())).thenReturn(projects);
        when(experienceMapper.selectList(any())).thenReturn(experiences);
        when(evidenceMapper.selectCount(any())).thenReturn(1L);
        when(calculator.calculate(profile, projects, experiences, true, true))
                .thenReturn(expected);

        CompletenessVO result = completenessService.get(1L);

        assertThat(result).isSameAs(expected);
        verify(calculator).calculate(profile, projects, experiences, true, true);
    }

    @Test
    void noProfileReturnsZeroWithoutLoadingRelatedData() {
        CompletenessVO expected = new CompletenessVO(0, List.of());
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(calculator.calculate(null, List.of(), List.of(), false, false))
                .thenReturn(expected);

        CompletenessVO result = completenessService.get(1L);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(projectMapper, experienceMapper, evidenceMapper);
    }

    @Test
    void passesFalseFlagsWhenNoConfirmedEvidenceExists() {
        UserProfile profile = profile();
        CompletenessVO expected = new CompletenessVO(10, List.of());
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(experienceMapper.selectList(any())).thenReturn(List.of());
        when(evidenceMapper.selectCount(any())).thenReturn(0L);
        when(calculator.calculate(profile, List.of(), List.of(), false, false))
                .thenReturn(expected);

        CompletenessVO result = completenessService.get(1L);

        assertThat(result).isSameAs(expected);
    }

    private static UserProfile profile() {
        UserProfile profile = new UserProfile();
        profile.setId(9L);
        profile.setUserId(1L);
        return profile;
    }
}
