package com.team.resume.resume.vo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 生成/保存新版本的返回：前端直接拿 content + templateId 渲染预览。
 */
public record ResumeGenerateResultVO(
        Long resumeId,
        Long versionId,
        Integer versionNumber,
        JsonNode content) {
}
