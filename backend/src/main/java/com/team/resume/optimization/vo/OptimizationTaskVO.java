package com.team.resume.optimization.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 优化任务信息（docs/api.md §10）。
 */
@Data
public class OptimizationTaskVO {

    private Long id;

    private Long jobId;

    private Long resumeVersionId;

    private Long chatSessionId;

    private String targetSkill;

    private String question;

    private String status;

    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
