package com.team.resume.resume.dto;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

/**
 * 手动保存新版本：编辑内容或切换模板都走这里，永不修改旧版本。
 */
@Data
public class SaveVersionRequest {

    private JsonNode content;

    private String templateId;

    private String generationReason;
}
