package com.team.resume.job.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.team.resume.ai.dto.JobRequirementsResult;
import com.team.resume.job.entity.JobRequirement;

/**
 * JD 技能要求的 Java 侧规则：
 * - 只保留非空技能，按技能名去重（忽略大小写）；
 * - importance 只允许 REQUIRED / PREFERRED，未知值按 REQUIRED；
 * - weight 归一化为合计 100.00，空值 / 非正数按 0 处理；
 * - AI 输出不直接落库，必须经过这里。
 */
public final class JobRequirementRules {

    private static final BigDecimal FULL_WEIGHT = new BigDecimal("100.00");

    private JobRequirementRules() {
    }

    public static List<JobRequirementsResult.Requirement> normalize(
            List<JobRequirementsResult.Requirement> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        Map<String, JobRequirementsResult.Requirement> unique = new LinkedHashMap<>();
        for (JobRequirementsResult.Requirement item : input) {
            if (item == null) {
                continue;
            }
            String skill = item.skill() == null ? "" : item.skill().trim();
            if (skill.isEmpty()) {
                continue;
            }
            String key = skill.toLowerCase(Locale.ROOT);
            if (unique.containsKey(key)) {
                continue;
            }
            String importance = JobRequirement.IMPORTANCE_PREFERRED.equalsIgnoreCase(item.importance())
                    ? JobRequirement.IMPORTANCE_PREFERRED
                    : JobRequirement.IMPORTANCE_REQUIRED;
            BigDecimal weight = item.weight() == null || item.weight().signum() <= 0
                    ? BigDecimal.ZERO
                    : item.weight();
            unique.put(key, new JobRequirementsResult.Requirement(skill, importance, weight));
        }

        if (unique.isEmpty()) {
            return List.of();
        }

        List<JobRequirementsResult.Requirement> entries = new ArrayList<>(unique.values());
        BigDecimal total = entries.stream()
                .map(JobRequirementsResult.Requirement::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<JobRequirementsResult.Requirement> result = new ArrayList<>(entries.size());
        BigDecimal assigned = BigDecimal.ZERO;
        for (int i = 0; i < entries.size(); i++) {
            JobRequirementsResult.Requirement entry = entries.get(i);
            BigDecimal weight;
            if (total.signum() > 0) {
                weight = entry.weight().multiply(FULL_WEIGHT)
                        .divide(total, 2, RoundingMode.HALF_UP);
            } else {
                weight = FULL_WEIGHT.divide(BigDecimal.valueOf(entries.size()), 2, RoundingMode.HALF_UP);
            }
            if (i == entries.size() - 1) {
                // 把四舍五入误差放到最后一项，保证合计始终是 100.00
                weight = FULL_WEIGHT.subtract(assigned);
            }
            assigned = assigned.add(weight);
            result.add(new JobRequirementsResult.Requirement(entry.skill(), entry.importance(), weight));
        }
        return List.copyOf(result);
    }
}
