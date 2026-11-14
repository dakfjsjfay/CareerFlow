package com.team.resume.application.vo;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 状态历史条目（时间轴）。
 */
@Data
public class ApplicationHistoryVO {

    private Long id;

    private String oldStatus;

    private String newStatus;

    private String note;

    private LocalDateTime createdAt;
}
