package com.team.resume.profile.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 个人档案（只含已确认数据，docs/api.md §6）。
 */
public record ProfileVO(
        Long id,
        String realName,
        String phone,
        String school,
        String major,
        String degree,
        Integer graduationYear,
        List<String> skills,
        String targetPosition,
        String targetCity,
        String expectedSalary,
        List<ProjectVO> projects,
        List<ExperienceVO> experiences) {

    public record ProjectVO(Long id, String projectName, String role, String description,
                            List<String> techStack, LocalDate startDate, LocalDate endDate) {
    }

    public record ExperienceVO(Long id, String company, String position, String description,
                               String achievements, LocalDate startDate, LocalDate endDate) {
    }
}
