package com.team.resume.resume.vo;

/**
 * 简历详情：currentVersion 可能为 null（刚创建、尚未生成）。
 */
public record ResumeDetailVO(
        Long id,
        String resumeName,
        Long currentVersionId,
        Integer currentVersionNumber,
        ResumeVersionVO currentVersion) {
}
