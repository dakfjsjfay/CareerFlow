package com.team.resume.optimization.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 确认补充信息后的响应：证据已写入档案，新简历版本已生成（docs/api.md §10）。
 */
@Data
@AllArgsConstructor
public class OptimizationConfirmVO {

    private Long taskId;

    private String taskStatus;

    private Long jobId;

    private Long resumeId;

    /** 重新生成的新版本 ID */
    private Long resumeVersionId;

    private Integer versionNumber;

    /** 本次写入档案的证据条数 */
    private int evidenceCount;

    /** 被更新的档案字段（API camelCase 路径） */
    private List<String> updatedFields;
}
