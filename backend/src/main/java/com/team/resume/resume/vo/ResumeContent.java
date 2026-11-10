package com.team.resume.resume.vo;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 结构化简历内容（docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md §6）。
 * 内容与样式分离：模板只读取 meta.templateId 对应的渲染组件。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResumeContent(
        Meta meta,
        PersonalInfo personalInfo,
        JobIntention jobIntention,
        List<Education> education,
        List<String> skills,
        List<Project> projects,
        List<Experience> experiences,
        String selfEvaluation) {

    public record Meta(
            String templateId,
            String templateName,
            String generatedAt,
            String source,
            String generationReason,
            Long targetJobId,
            List<String> warnings) {
    }

    public record PersonalInfo(
            String realName,
            String phone,
            String email,
            String city,
            String avatar,
            String school,
            String major,
            String degree,
            Integer graduationYear,
            Integer yearsOfExperience,
            @JsonProperty("abstract") String abstractText) {
    }

    public record JobIntention(String targetPosition, String targetCity, String expectedSalary) {
    }

    public record Education(
            String school,
            String major,
            String degree,
            Integer graduationYear,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record Project(
            String projectName,
            String role,
            String description,
            List<String> techStack,
            List<String> highlights,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record Experience(
            String company,
            String position,
            String type,
            String description,
            String achievements,
            List<String> highlights,
            LocalDate startDate,
            LocalDate endDate) {
    }
}
