package com.team.resume.application;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.team.resume.common.BusinessException;

/**
 * 投递状态机（docs/database.md §4）。
 * 状态只能由后端变更，非法流转一律 409。
 */
public enum ApplicationStatus {

  PENDING,
  APPLIED,
  READ,
  INTERVIEW_INVITED,
  INTERVIEWING,
  OFFERED,
  REJECTED,
  WITHDRAWN,
  FAILED,
  NO_RESPONSE;

  private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.of(
      PENDING, Set.of(APPLIED, WITHDRAWN),
      APPLIED, Set.of(READ, FAILED, WITHDRAWN, NO_RESPONSE),
      READ, Set.of(INTERVIEW_INVITED, NO_RESPONSE),
      INTERVIEW_INVITED, Set.of(INTERVIEWING, NO_RESPONSE),
      INTERVIEWING, Set.of(OFFERED, REJECTED),
      NO_RESPONSE, Set.of(READ, INTERVIEW_INVITED, REJECTED),
      OFFERED, Set.of(),
      REJECTED, Set.of(),
      WITHDRAWN, Set.of(),
      FAILED, Set.of());

  /** 需要主动提醒跟进的状态（database.md §5）。 */
  public static final Set<ApplicationStatus> FOLLOW_UP_STATUSES = Set.of(APPLIED, READ, INTERVIEW_INVITED, NO_RESPONSE);

  /** 已读及以后的状态，用于历史已读率统计。 */
  public static final Set<ApplicationStatus> READ_STATUSES = Set.of(READ, INTERVIEW_INVITED, INTERVIEWING, OFFERED,
      REJECTED);

  /** 面试及以后的状态，用于历史面试率统计。 */
  public static final Set<ApplicationStatus> INTERVIEW_STATUSES = Set.of(INTERVIEW_INVITED, INTERVIEWING, OFFERED);

  public boolean canTransitionTo(ApplicationStatus target) {
    return TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
  }

  public List<String> allowedTargets() {
    return TRANSITIONS.getOrDefault(this, Set.of()).stream()
        .map(Enum::name)
        .sorted()
        .toList();
  }

  public static ApplicationStatus from(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(400, "投递状态不能为空");
    }
    try {
      return ApplicationStatus.valueOf(value.trim().toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(400, "非法的投递状态：" + value);
    }
  }
}
