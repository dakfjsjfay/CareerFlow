package com.team.resume.job.vo;

import java.time.LocalDateTime;
import java.util.List;

public record JobDetailVO(
        Long id,
        String company,
        String position,
        String city,
        String salary,
        String jdContent,
        String sourceType,
        String externalUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<JobRequirementVO> requirements) {
}
