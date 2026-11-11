package com.team.resume.matching.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.team.resume.job.entity.JobRequirement;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.support.FactGuard;

/**
 * 匹配评分纯计算（docs/database.md §5 公式）：
 * M = Σ(wᵢ·sᵢ) / Σwᵢ × 100%
 *   sᵢ = 1     MATCHED（技能出现在项目 tech_stack，有项目证据）
 *         0.5   PARTIAL（技能在个人技能记录中，但无项目证据）
 *         0     MISSING / UNCONFIRMED（不计分，UNCONFIRMED 单独标记）
 *
 * 判定优先级：MATCHED > PARTIAL > UNCONFIRMED > MISSING。
 * 技能名归一化只去掉空格/连字符/下划线并转小写：
 * 「SpringBoot」能匹配「Spring Boot」，但「Spring」绝不等于「Spring Boot」。
 */
public final class MatchCalculator {

    public static final String MATCHED = "MATCHED";
    public static final String PARTIAL = "PARTIAL";
    public static final String MISSING = "MISSING";
    public static final String UNCONFIRMED = "UNCONFIRMED";

    private MatchCalculator() {
    }

    public record SkillMatch(String skill, String status, String evidence, String suggestion) {
    }

    public record Result(List<SkillMatch> details, BigDecimal score,
                         int matchedCount, int partialCount, int missingCount, int unconfirmedCount) {
    }

    /**
     * @param requirements      岗位技能要求
     * @param userSkills        已确认技能列表（user_profile.skills）
     * @param projects          已确认项目经历（tech_stack 为项目证据）
     * @param pendingEvidences  待确认证据（PENDING 状态，用于 UNCONFIRMED 标记）
     */
    public static Result calculate(List<JobRequirement> requirements,
                                   List<String> userSkills,
                                   List<ProfileProject> projects,
                                   List<ProfileEvidence> pendingEvidences) {
        // 归一化技能名 -> 项目名（同一个技能在多个项目中出现取第一个）
        Map<String, String> techEvidence = new HashMap<>();
        for (ProfileProject project : projects) {
            if (project.getTechStack() == null) {
                continue;
            }
            for (String tech : project.getTechStack()) {
                techEvidence.putIfAbsent(normalize(tech), project.getProjectName());
            }
        }

        List<SkillMatch> details = new ArrayList<>();
        int matched = 0;
        int partial = 0;
        int missing = 0;
        int unconfirmed = 0;
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal scoreSum = BigDecimal.ZERO;

        for (JobRequirement req : requirements) {
            SkillMatch match = evaluate(req, userSkills, techEvidence, pendingEvidences);
            details.add(match);

            switch (match.status()) {
                case MATCHED -> {
                    matched++;
                    weightSum = weightSum.add(req.getWeight());
                    scoreSum = scoreSum.add(req.getWeight());
                }
                case PARTIAL -> {
                    partial++;
                    weightSum = weightSum.add(req.getWeight());
                    scoreSum = scoreSum.add(req.getWeight().multiply(new BigDecimal("0.5")));
                }
                case UNCONFIRMED -> unconfirmed++;   // 不计分
                default -> {                         // 无证据：得 0 分，但权重计入分母（拉低总分）
                    missing++;
                    weightSum = weightSum.add(req.getWeight());
                }
            }
        }

        BigDecimal score = BigDecimal.ZERO;
        if (weightSum.compareTo(BigDecimal.ZERO) > 0) {
            score = scoreSum.multiply(BigDecimal.valueOf(100))
                    .divide(weightSum, 1, RoundingMode.HALF_UP);
        }
        return new Result(details, score, matched, partial, missing, unconfirmed);
    }

    private static SkillMatch evaluate(JobRequirement req, List<String> userSkills,
                                       Map<String, String> techEvidence,
                                       List<ProfileEvidence> pendingEvidences) {
        String skill = req.getSkill();
        String norm = normalize(skill);

        String projectName = techEvidence.get(norm);
        if (projectName != null) {
            return new SkillMatch(skill, MATCHED,
                    "项目《" + projectName + "》技术栈", "保留");
        }

        if (containsNormalized(userSkills, norm)) {
            return new SkillMatch(skill, PARTIAL,
                    "个人技能记录中有学习经历", "补充项目应用证据");
        }

        if (hasPendingEvidence(pendingEvidences, skill, norm)) {
            return new SkillMatch(skill, UNCONFIRMED,
                    "存在待确认信息（对话或文件分析产生）", "先在个人档案中确认该信息");
        }

        return new SkillMatch(skill, MISSING, null, "发起追问确认是否学习过");
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }

    private static boolean containsNormalized(List<String> list, String norm) {
        if (list == null || norm.isEmpty()) {
            return false;
        }
        return list.stream().anyMatch(v -> normalize(v).equals(norm));
    }

    private static boolean hasPendingEvidence(List<ProfileEvidence> pending, String skill, String norm) {
        // 技能名过短（如 "C"）不做文本匹配，避免误标待确认
        if (norm.length() < 2) {
            return false;
        }
        return pending.stream()
                .anyMatch(e -> FactGuard.isGrounded(e.getEvidenceContent(), skill));
    }
}
