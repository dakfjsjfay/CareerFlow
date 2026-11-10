package com.team.resume.profile.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.vo.CompletenessVO;

/**
 * 档案完整度计算（Java 硬编码，docs/database.md §6）。
 * <p>
 * 计分细则：
 * - 基础信息：real_name、phone 各 50；
 * - 教育经历：school、major、degree、graduation_year 各 25；
 * - 技能信息：空 0；1~2 条 50；≥3 条无 CONFIRMED 技能证据 75；≥3 条且至少 1 条 CONFIRMED 证据 100；
 * - 项目经历：无 0；有项目但缺 description/tech_stack 50；至少 1 条完整项目 100；
 * - 工作/实习经历：有经历或已确认「无经历」证据 100，否则 0；
 * - 求职意向：(target_position + target_city + 0.5 × expected_salary) / 2.5 × 100；
 * - 总分 = 6 项平均，四舍五入。
 */
@Component
public class CompletenessCalculator {

    public CompletenessVO calculate(UserProfile profile,
                                    List<ProfileProject> projects,
                                    List<ProfileExperience> experiences,
                                    boolean hasConfirmedSkillEvidence,
                                    boolean hasConfirmedNoExperience) {
        List<CompletenessVO.DimensionScore> dimensions = List.of(
                new CompletenessVO.DimensionScore("基础信息", scoreBasic(profile)),
                new CompletenessVO.DimensionScore("教育经历", scoreEducation(profile)),
                new CompletenessVO.DimensionScore("技能信息",
                        scoreSkills(profile, hasConfirmedSkillEvidence)),
                new CompletenessVO.DimensionScore("项目经历", scoreProjects(projects)),
                new CompletenessVO.DimensionScore("工作/实习经历",
                        scoreExperience(experiences, hasConfirmedNoExperience)),
                new CompletenessVO.DimensionScore("求职意向", scoreIntention(profile)));

        int overall = (int) Math.round(dimensions.stream()
                .mapToInt(CompletenessVO.DimensionScore::score)
                .average()
                .orElse(0));
        return new CompletenessVO(overall, dimensions);
    }

    private static int scoreBasic(UserProfile profile) {
        if (profile == null) {
            return 0;
        }
        return (hasText(profile.getRealName()) ? 50 : 0)
                + (hasText(profile.getPhone()) ? 50 : 0);
    }

    private static int scoreEducation(UserProfile profile) {
        if (profile == null) {
            return 0;
        }
        return (hasText(profile.getSchool()) ? 25 : 0)
                + (hasText(profile.getMajor()) ? 25 : 0)
                + (hasText(profile.getDegree()) ? 25 : 0)
                + (profile.getGraduationYear() != null ? 25 : 0);
    }

    private static int scoreSkills(UserProfile profile, boolean hasConfirmedSkillEvidence) {
        if (profile == null || profile.getSkills() == null) {
            return 0;
        }
        long count = profile.getSkills().stream().filter(CompletenessCalculator::hasText).count();
        if (count == 0) {
            return 0;
        }
        if (count < 3) {
            return 50;
        }
        return hasConfirmedSkillEvidence ? 100 : 75;
    }

    private static int scoreProjects(List<ProfileProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return 0;
        }
        boolean complete = projects.stream().anyMatch(CompletenessCalculator::isCompleteProject);
        return complete ? 100 : 50;
    }

    private static boolean isCompleteProject(ProfileProject project) {
        return project != null
                && hasText(project.getProjectName())
                && hasText(project.getDescription())
                && project.getTechStack() != null
                && project.getTechStack().stream().anyMatch(CompletenessCalculator::hasText);
    }

    private static int scoreExperience(List<ProfileExperience> experiences,
                                       boolean hasConfirmedNoExperience) {
        if ((experiences != null && !experiences.isEmpty()) || hasConfirmedNoExperience) {
            return 100;
        }
        return 0;
    }

    private static int scoreIntention(UserProfile profile) {
        if (profile == null) {
            return 0;
        }
        double filled = (hasText(profile.getTargetPosition()) ? 1 : 0)
                + (hasText(profile.getTargetCity()) ? 1 : 0)
                + (hasText(profile.getExpectedSalary()) ? 0.5 : 0);
        return (int) Math.round(filled / 2.5 * 100);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
