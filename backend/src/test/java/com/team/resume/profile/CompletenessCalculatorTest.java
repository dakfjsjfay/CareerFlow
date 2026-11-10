package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.service.CompletenessCalculator;
import com.team.resume.profile.vo.CompletenessVO;

class CompletenessCalculatorTest {

    private final CompletenessCalculator calculator = new CompletenessCalculator();

    @Test
    void emptyProfileScoresZeroInEveryDimension() {
        CompletenessVO result = calculator.calculate(null, List.of(), List.of(), false, false);

        assertThat(result.overall()).isZero();
        assertThat(result.dimensions()).extracting(CompletenessVO.DimensionScore::name)
                .containsExactly("基础信息", "教育经历", "技能信息", "项目经历", "工作/实习经历", "求职意向");
        assertThat(result.dimensions()).extracting(CompletenessVO.DimensionScore::score)
                .containsExactly(0, 0, 0, 0, 0, 0);
    }

    @Test
    void fullProfileScoresOneHundred() {
        UserProfile profile = profile();
        profile.setRealName("张三");
        profile.setPhone("13800000000");
        profile.setSchool("某某大学");
        profile.setMajor("计算机科学与技术");
        profile.setDegree("本科");
        profile.setGraduationYear(2027);
        profile.setSkills(List.of("Java", "MySQL", "Redis"));
        profile.setTargetPosition("Java 后端");
        profile.setTargetCity("杭州");
        profile.setExpectedSalary("8-12K");

        CompletenessVO result = calculator.calculate(
                profile,
                List.of(project("学生管理系统", "增删改查", List.of("Java", "MySQL"))),
                List.of(experience("某某科技")),
                true,
                false);

        assertThat(result.dimensions()).extracting(CompletenessVO.DimensionScore::score)
                .containsExactly(100, 100, 100, 100, 100, 100);
        assertThat(result.overall()).isEqualTo(100);
    }

    @Test
    void partialProfileFollowsTheDocumentedWeights() {
        UserProfile profile = profile();
        profile.setRealName("张三");
        profile.setSchool("某某大学");
        profile.setMajor("计算机科学与技术");
        profile.setSkills(List.of("Java", "MySQL"));
        profile.setTargetPosition("Java 后端");

        CompletenessVO result = calculator.calculate(
                profile,
                List.of(project("学生管理系统", null, List.of())),
                List.of(),
                false,
                true);

        // 基础 50；教育 50；技能 50；项目 50；经历 100（明确无经历）；求职意向 40
        assertThat(result.dimensions()).extracting(CompletenessVO.DimensionScore::score)
                .containsExactly(50, 50, 50, 50, 100, 40);
        assertThat(result.overall()).isEqualTo(57); // 340 / 6 = 56.67 → 57
    }

    @Test
    void threeSkillsNeedConfirmedEvidenceForFullScore() {
        UserProfile profile = profile();
        profile.setSkills(List.of("Java", "MySQL", "Redis"));

        CompletenessVO withoutEvidence = calculator.calculate(
                profile, List.of(), List.of(), false, false);
        CompletenessVO withEvidence = calculator.calculate(
                profile, List.of(), List.of(), true, false);

        assertThat(scoreOf(withoutEvidence, "技能信息")).isEqualTo(75);
        assertThat(scoreOf(withEvidence, "技能信息")).isEqualTo(100);
    }

    @Test
    void intentionCountsExpectedSalaryAsHalfAnItem() {
        UserProfile positionOnly = profile();
        positionOnly.setTargetPosition("Java 后端");
        UserProfile bothTargets = profile();
        bothTargets.setTargetPosition("Java 后端");
        bothTargets.setTargetCity("杭州");
        UserProfile positionAndSalary = profile();
        positionAndSalary.setTargetPosition("Java 后端");
        positionAndSalary.setExpectedSalary("8-12K");

        assertThat(scoreOf(calculator.calculate(positionOnly, List.of(), List.of(), false, false),
                "求职意向")).isEqualTo(40);
        assertThat(scoreOf(calculator.calculate(bothTargets, List.of(), List.of(), false, false),
                "求职意向")).isEqualTo(80);
        assertThat(scoreOf(calculator.calculate(positionAndSalary, List.of(), List.of(), false, false),
                "求职意向")).isEqualTo(60);
    }

    @Test
    void experienceDimensionAcceptsExplicitNoExperienceEvidence() {
        CompletenessVO noExperience = calculator.calculate(
                profile(), List.of(), List.of(), false, true);
        CompletenessVO unknown = calculator.calculate(
                profile(), List.of(), List.of(), false, false);

        assertThat(scoreOf(noExperience, "工作/实习经历")).isEqualTo(100);
        assertThat(scoreOf(unknown, "工作/实习经历")).isZero();
    }

    @Test
    void incompleteProjectScoresHalf() {
        CompletenessVO incomplete = calculator.calculate(
                profile(),
                List.of(project("学生管理系统", "只写了描述", List.of())),
                List.of(),
                false,
                false);
        CompletenessVO complete = calculator.calculate(
                profile(),
                List.of(
                        project("不完整项目", null, List.of()),
                        project("学生管理系统", "增删改查", List.of("Java"))),
                List.of(),
                false,
                false);

        assertThat(scoreOf(incomplete, "项目经历")).isEqualTo(50);
        assertThat(scoreOf(complete, "项目经历")).isEqualTo(100);
    }

    private static int scoreOf(CompletenessVO result, String name) {
        return result.dimensions().stream()
                .filter(dimension -> dimension.name().equals(name))
                .findFirst()
                .orElseThrow()
                .score();
    }

    private static UserProfile profile() {
        UserProfile profile = new UserProfile();
        profile.setId(1L);
        profile.setUserId(1L);
        return profile;
    }

    private static ProfileProject project(String name, String description, List<String> techStack) {
        ProfileProject project = new ProfileProject();
        project.setProjectName(name);
        project.setDescription(description);
        project.setTechStack(techStack);
        return project;
    }

    private static ProfileExperience experience(String company) {
        ProfileExperience experience = new ProfileExperience();
        experience.setCompany(company);
        return experience;
    }
}
