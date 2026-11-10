package com.team.resume.resume.dto;

import lombok.Data;

/**
 * 导出 DOCX：versionId 必填，format 只接受 DOCX。
 * PDF 由前端浏览器打印生成，不再经过后端。
 */
@Data
public class ExportResumeRequest {

    private Long versionId;

    private String format;
}
