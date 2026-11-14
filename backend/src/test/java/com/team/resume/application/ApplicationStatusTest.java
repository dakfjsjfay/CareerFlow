package com.team.resume.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.team.resume.common.BusinessException;

/**
 * 投递状态机单元测试（docs/database.md §4）。
 */
class ApplicationStatusTest {

    @Test
    void allowsOnlyDocumentedTransitions() {
        assertTrue(ApplicationStatus.PENDING.canTransitionTo(ApplicationStatus.APPLIED));
        assertTrue(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.READ));
        assertTrue(ApplicationStatus.READ.canTransitionTo(ApplicationStatus.INTERVIEW_INVITED));
        assertTrue(ApplicationStatus.INTERVIEW_INVITED.canTransitionTo(ApplicationStatus.INTERVIEWING));
        assertTrue(ApplicationStatus.INTERVIEWING.canTransitionTo(ApplicationStatus.OFFERED));
        assertTrue(ApplicationStatus.NO_RESPONSE.canTransitionTo(ApplicationStatus.INTERVIEW_INVITED));

        assertFalse(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.OFFERED));
        assertFalse(ApplicationStatus.READ.canTransitionTo(ApplicationStatus.INTERVIEWING));
        assertFalse(ApplicationStatus.INTERVIEWING.canTransitionTo(ApplicationStatus.APPLIED));
    }

    @Test
    void terminalStatusesHaveNoOutgoingTransitions() {
        assertEquals(0, ApplicationStatus.OFFERED.allowedTargets().size());
        assertEquals(0, ApplicationStatus.REJECTED.allowedTargets().size());
        assertEquals(0, ApplicationStatus.WITHDRAWN.allowedTargets().size());
        assertEquals(0, ApplicationStatus.FAILED.allowedTargets().size());
    }

    @Test
    void rejectsUnknownStatusWithBadRequest() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> ApplicationStatus.from("NOT_A_STATUS"));
        assertEquals(400, error.getCode());
    }

    @Test
    void parsesStatusIgnoringCase() {
        assertEquals(ApplicationStatus.INTERVIEW_INVITED,
                ApplicationStatus.from("interview_invited"));
    }
}
