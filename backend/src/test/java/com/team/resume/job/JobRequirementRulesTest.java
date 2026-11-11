package com.team.resume.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.team.resume.ai.dto.JobRequirementsResult.Requirement;
import com.team.resume.job.entity.JobRequirement;
import com.team.resume.job.service.JobRequirementRules;

class JobRequirementRulesTest {

    @Test
    void keepsWeightsWhenTheyAlreadySumTo100() {
        List<Requirement> result = JobRequirementRules.normalize(List.of(
                req("Java", "REQUIRED", "25"),
                req("Spring Boot", "REQUIRED", "25"),
                req("MySQL", "REQUIRED", "20"),
                req("Redis", "PREFERRED", "15"),
                req("Docker", "PREFERRED", "15")));

        assertThat(result).extracting(Requirement::weight)
                .containsExactly(decimal("25.00"), decimal("25.00"), decimal("20.00"),
                        decimal("15.00"), decimal("15.00"));
    }

    @Test
    void equalizesZeroWeightsAndKeepsTotalAt100() {
        List<Requirement> result = JobRequirementRules.normalize(List.of(
                req("Java", "REQUIRED", "0"),
                req("MySQL", "REQUIRED", null),
                req("Redis", "PREFERRED", "-1")));

        assertThat(result).extracting(Requirement::weight)
                .containsExactly(decimal("33.33"), decimal("33.33"), decimal("33.34"));
        assertThat(sum(result)).isEqualByComparingTo("100.00");
    }

    @Test
    void scalesArbitraryWeights() {
        List<Requirement> result = JobRequirementRules.normalize(List.of(
                req("Java", "REQUIRED", "1"),
                req("MySQL", "REQUIRED", "2")));

        assertThat(result).extracting(Requirement::weight)
                .containsExactly(decimal("33.33"), decimal("66.67"));
    }

    @Test
    void deduplicatesSkillsIgnoringCase() {
        List<Requirement> result = JobRequirementRules.normalize(List.of(
                req("Java", "REQUIRED", "50"),
                req("java", "PREFERRED", "50")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).skill()).isEqualTo("Java");
    }

    @Test
    void defaultsUnknownImportanceToRequired() {
        List<Requirement> result = JobRequirementRules.normalize(List.of(
                req("Java", "NICE_TO_HAVE", "50"),
                req("MySQL", "preferred", "50")));

        assertThat(result).extracting(Requirement::importance)
                .containsExactly(JobRequirement.IMPORTANCE_REQUIRED,
                        JobRequirement.IMPORTANCE_PREFERRED);
    }

    @Test
    void dropsBlankAndNullSkills() {
        List<Requirement> result = JobRequirementRules.normalize(Arrays.asList(
                req("  ", "REQUIRED", "50"),
                null,
                req("Java", "REQUIRED", "50")));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).skill()).isEqualTo("Java");
    }

    private static Requirement req(String skill, String importance, String weight) {
        return new Requirement(skill, importance, weight == null ? null : new BigDecimal(weight));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal sum(List<Requirement> requirements) {
        return requirements.stream()
                .map(Requirement::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
