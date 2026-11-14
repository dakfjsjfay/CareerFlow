package com.team.resume.application.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 投递记录视图（列表 / 详情 / 状态更新统一返回）。
 */
@Data
public class ApplicationVO {

    private Long id;

    private Long jobId;

    private String company;

    private String position;

    private String city;

    private String salary;

    private Long resumeVersionId;

    private Integer versionNumber;

    private String status;

    private LocalDateTime appliedAt;

    private LocalDateTime nextFollowUpAt;

    private LocalDateTime lastFollowUpAt;

    private LocalDateTime updatedAt;
}
