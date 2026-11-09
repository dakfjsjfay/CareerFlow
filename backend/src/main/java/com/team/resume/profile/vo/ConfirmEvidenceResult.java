package com.team.resume.profile.vo;

import java.util.List;

/**
 * 确认接口返回（docs/api.md §6.1）：
 * { "confirmedCount": 2, "updatedFields": ["skills", "project.techStack"] }
 * confirmedCount 只统计本次真正变为 CONFIRMED 的证据；拒绝时恒为 0。
 */
public record ConfirmEvidenceResult(int confirmedCount, List<String> updatedFields) {
}
