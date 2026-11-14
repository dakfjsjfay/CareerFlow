package com.team.resume.resume.dto;

import lombok.Data;

/**
 * 导出简历版本：versionId 必填，format 支持 DOCX / PDF（默认 DOCX）。
 */
@Data
public class ExportResumeRequest {

    private Long versionId;

    private String format;
}
