package com.team.resume.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team.resume.analytics.service.FeedbackAnalyticsService;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO.FeedbackDistribution;
import com.team.resume.analytics.vo.FeedbackAnalyticsVO.VersionEffect;
import com.team.resume.application.entity.ApplicationHistory;
import com.team.resume.application.entity.JobApplication;
import com.team.resume.application.mapper.ApplicationHistoryMapper;
import com.team.resume.application.mapper.JobApplicationMapper;
import com.team.resume.matching.entity.UserFeedback;
import com.team.resume.matching.mapper.UserFeedbackMapper;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeVersionMapper;

@ExtendWith(MockitoExtension.class)
class FeedbackAnalyticsServiceTest {

    @Mock
    private JobApplicationMapper applicationMapper;

    @Mock
    private ApplicationHistoryMapper historyMapper;

    @Mock
    private ResumeVersionMapper resumeVersionMapper;

    @Mock
    private UserFeedbackMapper feedbackMapper;

    private FeedbackAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new FeedbackAnalyticsService(
                applicationMapper, historyMapper, resumeVersionMapper, feedbackMapper);
    }

    @Test
    void aggregatesFunnelVersionEffectsAndFeedbackDistribution() {
        JobApplication offered = application(1L, 20L, "OFFERED");
        JobApplication rejected = application(2L, 20L, "REJECTED");
        JobApplication applied = application(3L, 10L, "APPLIED");

        when(applicationMapper.selectList(any())).thenReturn(List.of(offered, rejected, applied));
        when(historyMapper.selectList(any())).thenReturn(List.of(
                history(1L, "APPLIED", "READ"),
                history(1L, "READ", "INTERVIEW_INVITED"),
                history(1L, "INTERVIEW_INVITED", "INTERVIEWING"),
                history(1L, "INTERVIEWING", "OFFERED"),
                history(2L, "APPLIED", "READ"),
                history(2L, "READ", "INTERVIEW_INVITED"),
                history(2L, "INTERVIEW_INVITED", "INTERVIEWING"),
                history(2L, "INTERVIEWING", "REJECTED"),
                history(3L, "PENDING", "APPLIED")));
        when(resumeVersionMapper.selectBatchIds(any()))
                .thenReturn(List.of(version(10L, 1), version(20L, 2)));
        when(feedbackMapper.selectList(any())).thenReturn(List.of(
                feedback("NOT_INTERESTED"),
                feedback("NOT_INTERESTED"),
                feedback("LOCATION_MISMATCH")));

        FeedbackAnalyticsVO result = service.analyze(7L);

        assertThat(result.getSummary().getTotalApplications()).isEqualTo(3);
        assertThat(result.getSummary().getReadCount()).isEqualTo(2);
        assertThat(result.getSummary().getInterviewCount()).isEqualTo(2);
        assertThat(result.getSummary().getOfferCount()).isEqualTo(1);
        assertThat(result.getSummary().getFeedbackCount()).isEqualTo(3);
        assertThat(result.getSummary().getReadRate()).isCloseTo(0.6667, within(0.0001));
        assertThat(result.getSummary().getInterviewRate()).isCloseTo(0.6667, within(0.0001));
        assertThat(result.getSummary().getOfferRate()).isCloseTo(0.3333, within(0.0001));

        assertThat(result.getFunnel()).hasSize(4);
        assertThat(result.getFunnel().get(0).getCount()).isEqualTo(3);
        assertThat(result.getFunnel().get(3).getLabel()).isEqualTo("录用");

        assertThat(result.getVersionEffects()).hasSize(2);
        VersionEffect latestVersion = result.getVersionEffects().get(0);
        assertThat(latestVersion.getVersionNumber()).isEqualTo(2);
        assertThat(latestVersion.getApplicationCount()).isEqualTo(2);
        assertThat(latestVersion.getReadCount()).isEqualTo(2);
        assertThat(latestVersion.getInterviewCount()).isEqualTo(2);
        assertThat(latestVersion.getOfferCount()).isEqualTo(1);
        assertThat(latestVersion.getInterviewRate()).isCloseTo(1.0, within(0.0001));

        VersionEffect firstVersion = result.getVersionEffects().get(1);
        assertThat(firstVersion.getVersionNumber()).isEqualTo(1);
        assertThat(firstVersion.getApplicationCount()).isEqualTo(1);
        assertThat(firstVersion.getReadRate()).isZero();

        assertThat(result.getFeedbackDistribution()).hasSize(4);
        FeedbackDistribution notInterested = result.getFeedbackDistribution().stream()
                .filter(item -> item.getFeedbackType().equals("NOT_INTERESTED"))
                .findFirst()
                .orElseThrow();
        assertThat(notInterested.getCount()).isEqualTo(2);
        assertThat(notInterested.getRate()).isCloseTo(0.6667, within(0.0001));
    }

    @Test
    void returnsZeroRatesAndEmptyVersionsWhenUserHasNoApplications() {
        when(applicationMapper.selectList(any())).thenReturn(List.of());
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        FeedbackAnalyticsVO result = service.analyze(7L);

        assertThat(result.getSummary().getTotalApplications()).isZero();
        assertThat(result.getSummary().getReadRate()).isZero();
        assertThat(result.getSummary().getOfferRate()).isZero();
        assertThat(result.getFunnel()).hasSize(4);
        assertThat(result.getFunnel()).allSatisfy(stage -> {
            assertThat(stage.getCount()).isZero();
            assertThat(stage.getRate()).isZero();
        });
        assertThat(result.getVersionEffects()).isEmpty();
        assertThat(result.getFeedbackDistribution()).hasSize(4);
        assertThat(result.getFeedbackDistribution()).allMatch(item -> item.getCount() == 0);
    }

    private static JobApplication application(Long id, Long versionId, String status) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setResumeVersionId(versionId);
        application.setStatus(status);
        return application;
    }

    private static ApplicationHistory history(Long applicationId, String oldStatus, String newStatus) {
        ApplicationHistory history = new ApplicationHistory();
        history.setApplicationId(applicationId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        return history;
    }

    private static ResumeVersion version(Long id, Integer versionNumber) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setVersionNumber(versionNumber);
        return version;
    }

    private static UserFeedback feedback(String type) {
        UserFeedback feedback = new UserFeedback();
        feedback.setFeedbackType(type);
        return feedback;
    }
}
