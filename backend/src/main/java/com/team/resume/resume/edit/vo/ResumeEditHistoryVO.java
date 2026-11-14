package com.team.resume.resume.edit.vo;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 修改历史详情：beforeContent / afterContent 用于前端展示前后对比。
 */
public record ResumeEditHistoryVO(Long id,
                                  Long resumeId,
                                  Long draftId,
                                  String sectionKey,
                                  JsonNode beforeContent,
                                  JsonNode afterContent,
                                  String source,
                                  String status,
                                  List<String> warnings,
                                  String promptSummary,
                                  String model,
                                  LocalDateTime createdAt,
                                  LocalDateTime appliedAt) {
}
