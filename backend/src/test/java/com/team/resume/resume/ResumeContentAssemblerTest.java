package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.team.resume.common.BusinessException;
import com.team.resume.profile.vo.ProfileVO;
import com.team.resume.resume.service.ResumeContentAssembler;
import com.team.resume.resume.vo.ResumeContent;

/**
 * 事实组装只允许搬运已确认档案中的内容，不允许补全或推断用户没提供的信息。
 */
class ResumeContentAssemblerTest {

    private final ResumeContentAssembler assembler = new ResumeContentAssembler();

    @Test
    void classifiesInternshipByKeyword() {
        assertEquals("INTERNSHIP", ResumeContentAssembler.classifyExperience(experience("某公司", "后端实习生")));
        assertEquals("INTERNSHIP", ResumeContentAssembler.classifyExperience(experience("Intern Co.", "Engineer")));
        assertEquals("WORK", ResumeContentAssembler.classifyExperience(experience("某公司", "后端工程师")));
    }

    @Test
    void computesYearsFromExperienceRanges() {
        List<ProfileVO.ExperienceVO> experiences = List.of(
                experienceRange("2022-01-01", "2023-01-01"),
                experienceRange("2024-01-01", "2025-01-01"));
        assertEquals(2, ResumeContentAssembler.yearsOfExperience(
                experiences, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void yearsOfExperienceIgnoresMissingOrInvalidRanges() {
        List<ProfileVO.ExperienceVO> experiences = List.of(
                experience("某公司", "实习生"),
                experienceRange("2025-01-01", "2024-01-01"));
        assertEquals(0, ResumeContentAssembler.yearsOfExperience(
                experiences, LocalDate.of(2026, 1, 1)));
    }

    @Test
    void mapsProfileWithoutInventingFields() {
        ProfileVO profile = profile(
                "张同学", "13800000000", "深圳大学", "软件工程", "本科", 2028,
                List.of("Java", "MySQL"), "Java 开发实习生", "深圳", "面议",
                List.of(), List.of());

        ResumeContent content = assembler.assemble(
                profile, "我的简历", "tpl-1", "模板一", "INITIAL", null);

        assertEquals("张同学", content.personalInfo().realName());
        assertEquals("13800000000", content.personalInfo().phone());
        assertNull(content.personalInfo().email());
        assertNull(content.personalInfo().city());
        assertNull(content.personalInfo().avatar());
        assertEquals("深圳大学", content.education().get(0).school());
        assertEquals("Java 开发实习生", content.jobIntention().targetPosition());
        assertTrue(content.projects().isEmpty());
        assertTrue(content.experiences().isEmpty());
        assertEquals("tpl-1", content.meta().templateId());
        assertEquals("AI", content.meta().source());
        assertTrue(content.meta().warnings().isEmpty());
    }

    @Test
    void rejectsEmptyProfile() {
        ProfileVO empty = profile(null, null, null, null, null, null,
                List.of(), null, null, null, List.of(), List.of());

        BusinessException error = assertThrows(BusinessException.class, () -> assembler.assemble(
                empty, "我的简历", "tpl-1", "模板一", "INITIAL", null));

        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("档案信息不足"));
    }

    @Test
    void createsEducationOnlyWhenSchoolOrMajorExists() {
        ProfileVO profile = profile("张同学", null, null, "软件工程", null, null,
                List.of("Java"), null, null, null, List.of(), List.of());

        ResumeContent content = assembler.assemble(
                profile, "我的简历", "tpl-1", "模板一", "INITIAL", null);

        assertEquals(1, content.education().size());
        assertEquals("软件工程", content.education().get(0).major());
    }

    private static ProfileVO profile(String realName, String phone, String school, String major,
                                     String degree, Integer graduationYear, List<String> skills,
                                     String targetPosition, String targetCity, String expectedSalary,
                                     List<ProfileVO.ProjectVO> projects,
                                     List<ProfileVO.ExperienceVO> experiences) {
        return new ProfileVO(1L, realName, phone, school, major, degree, graduationYear,
                skills, targetPosition, targetCity, expectedSalary, projects, experiences);
    }

    private static ProfileVO.ExperienceVO experience(String company, String position) {
        return new ProfileVO.ExperienceVO(1L, company, position, null, null, null, null);
    }

    private static ProfileVO.ExperienceVO experienceRange(String start, String end) {
        return new ProfileVO.ExperienceVO(1L, "某公司", "工程师", null, null,
                LocalDate.parse(start), LocalDate.parse(end));
    }
}
