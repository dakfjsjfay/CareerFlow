package com.team.resume.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 处理跟进提醒（docs/api.md §11.2）。
 * nextFollowUpAt 以字符串接收，兼容带时区（2026-11-15T10:00:00+08:00）与本地时间两种格式。
 */
@Data
public class FollowUpRequest {

    @NotBlank(message = "action 不能为空")
    private String action;

    @Size(max = 500, message = "跟进说明不能超过 500 字")
    private String note;

    private String nextFollowUpAt;
}
