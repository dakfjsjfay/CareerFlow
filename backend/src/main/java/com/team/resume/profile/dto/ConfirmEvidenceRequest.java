package com.team.resume.profile.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * 确认/拒绝抽取信息（docs/api.md §6.1）：
 * { "evidenceIds": [101, 102], "confirmed": true }
 */
@Data
public class ConfirmEvidenceRequest {

    @NotEmpty(message = "证据 ID 不能为空")
    @Size(max = 100, message = "单次最多处理 100 条证据")
    private List<@NotNull(message = "证据 ID 不能为空") Long> evidenceIds;

    @NotNull(message = "confirmed 不能为空")
    private Boolean confirmed;
}
