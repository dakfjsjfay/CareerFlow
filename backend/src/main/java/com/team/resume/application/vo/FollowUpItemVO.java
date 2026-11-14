package com.team.resume.application.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 待跟进条目（docs/api.md §11.1）。
 */
@Data
public class FollowUpItemVO {

    private Long applicationId;

    private Long jobId;

    private String company;

    private String position;

    private String status;

    private LocalDateTime lastUpdateAt;

    private long daysNoResponse;
}
