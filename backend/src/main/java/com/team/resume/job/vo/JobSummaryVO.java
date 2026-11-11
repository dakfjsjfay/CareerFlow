package com.team.resume.job.vo;

import java.time.LocalDateTime;

public record JobSummaryVO(
        Long id,
        String company,
        String position,
        String city,
        String salary,
        String sourceType,
        String externalUrl,
        LocalDateTime createdAt) {
}
