package com.team.resume.resume.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.team.resume.common.BusinessException;
import com.team.resume.profile.vo.ProfileVO;
import com.team.resume.resume.vo.ResumeContent;

/**
 * 档案 → 简历内容的事实组装（docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md §8.3）。
 * <p>
 * 只搬运 CONFIRMED 档案里已有的字段；缺失字段保持 null，绝不补全。
 * AI 只在 {@link ResumeAiService} 中改写文案，事实部分由这里确定。
 */
@Component
public class ResumeContentAssembler {

    private static final int ABSTRACT_MAX_LENGTH = 60;

    public ResumeContent assemble(ProfileVO profile, String resumeName, String templateId,
                                  String templateName, String generationReason, Long targetJobId) {
        if (profile == null || isEmpty(profile)) {
            throw new BusinessException(400, "档案信息不足，请先完成对话建档或档案编辑");
        }

        List<String> skills = profile.skills() == null ? List.of() : profile.skills();
        List<ProfileVO.ProjectVO> projects = profile.projects() == null ? List.of() : profile.projects();
        List<ProfileVO.ExperienceVO> experiences =
                profile.experiences() == null ? List.of() : profile.experiences();

        String selfEvaluation = fallbackSelfEvaluation(profile);
        ResumeContent.PersonalInfo personalInfo = new ResumeContent.PersonalInfo(
                trimToNull(profile.realName()),
                trimToNull(profile.phone()),
                null,
                null,
                null,
                trimToNull(profile.school()),
                trimToNull(profile.major()),
                trimToNull(profile.degree()),
                profile.graduationYear(),
                yearsOfExperience(experiences, LocalDate.now()),
                firstSentence(selfEvaluation));

        ResumeContent.JobIntention jobIntention = new ResumeContent.JobIntention(
                trimToNull(profile.targetPosition()),
                trimToNull(profile.targetCity()),
                trimToNull(profile.expectedSalary()));

        List<ResumeContent.Education> education = new ArrayList<>();
        if (notBlank(profile.school()) || notBlank(profile.major())) {
            education.add(new ResumeContent.Education(
                    trimToNull(profile.school()),
                    trimToNull(profile.major()),
                    trimToNull(profile.degree()),
                    profile.graduationYear(),
                    null,
                    null));
        }

        List<ResumeContent.Project> projectContents = projects.stream()
                .map(project -> new ResumeContent.Project(
                        trimToNull(project.projectName()),
                        trimToNull(project.role()),
                        trimToNull(project.description()),
                        project.techStack() == null ? List.of() : project.techStack(),
                        List.of(),
                        project.startDate(),
                        project.endDate()))
                .toList();

        List<ResumeContent.Experience> experienceContents = experiences.stream()
                .map(experience -> new ResumeContent.Experience(
                        trimToNull(experience.company()),
                        trimToNull(experience.position()),
                        classifyExperience(experience),
                        trimToNull(experience.description()),
                        trimToNull(experience.achievements()),
                        List.of(),
                        experience.startDate(),
                        experience.endDate()))
                .toList();

        ResumeContent.Meta meta = new ResumeContent.Meta(
                templateId,
                templateName,
                LocalDateTime.now().toString(),
                "AI",
                generationReason,
                targetJobId,
                new ArrayList<>());

        return new ResumeContent(meta, personalInfo, jobIntention, education, List.copyOf(skills),
                projectContents, experienceContents, selfEvaluation);
    }

    /**
     * 只用档案中已有的事实拼出一句兜底自我评价；没有可用事实时返回空串。
     */
    public static String fallbackSelfEvaluation(ProfileVO profile) {
        List<String> parts = new ArrayList<>();
        if (notBlank(profile.school())) {
            parts.add("就读于" + profile.school().trim());
        }
        if (notBlank(profile.degree()) || notBlank(profile.major())) {
            String degree = notBlank(profile.degree()) ? profile.degree().trim() : "";
            String major = notBlank(profile.major()) ? "主修" + profile.major().trim() : "";
            String text = (degree + (degree.isEmpty() || major.isEmpty() ? "" : "，") + major).trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
        }
        if (profile.skills() != null && !profile.skills().isEmpty()) {
            parts.add("掌握" + String.join("、", profile.skills()));
        }
        if (profile.projects() != null && !profile.projects().isEmpty()) {
            parts.add("有 " + profile.projects().size() + " 个项目经验");
        }
        if (profile.experiences() != null && !profile.experiences().isEmpty()) {
            parts.add("有 " + profile.experiences().size() + " 段工作/实习经历");
        }
        if (notBlank(profile.targetPosition())) {
            parts.add("目标岗位：" + profile.targetPosition().trim());
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join("，", parts) + "。";
    }

    /**
     * 累计所有有起止时间的经历月数后取整年；缺失或倒置的区间不计入。
     */
    public static int yearsOfExperience(List<ProfileVO.ExperienceVO> experiences, LocalDate today) {
        if (experiences == null || experiences.isEmpty()) {
            return 0;
        }
        long months = 0;
        for (ProfileVO.ExperienceVO experience : experiences) {
            if (experience == null || experience.startDate() == null) {
                continue;
            }
            LocalDate end = experience.endDate() == null ? today : experience.endDate();
            if (end.isBefore(experience.startDate())) {
                continue;
            }
            months += ChronoUnit.MONTHS.between(
                    experience.startDate().withDayOfMonth(1),
                    end.withDayOfMonth(1));
        }
        return (int) (months / 12);
    }

    public static String classifyExperience(ProfileVO.ExperienceVO experience) {
        if (experience == null) {
            return "WORK";
        }
        String text = ((experience.position() == null ? "" : experience.position())
                + " "
                + (experience.company() == null ? "" : experience.company()))
                .toLowerCase(Locale.ROOT);
        return text.contains("实习") || text.contains("intern") ? "INTERNSHIP" : "WORK";
    }

    private static boolean isEmpty(ProfileVO profile) {
        return !notBlank(profile.realName())
                && !notBlank(profile.school())
                && !notBlank(profile.major())
                && (profile.skills() == null || profile.skills().isEmpty())
                && (profile.projects() == null || profile.projects().isEmpty())
                && (profile.experiences() == null || profile.experiences().isEmpty());
    }

    private static String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        int end = -1;
        for (char mark : new char[] {'。', '！', '？', '!', '?'}) {
            int index = trimmed.indexOf(mark);
            if (index >= 0 && (end < 0 || index < end)) {
                end = index;
            }
        }
        String sentence = end >= 0 ? trimmed.substring(0, end + 1) : trimmed;
        return sentence.length() > ABSTRACT_MAX_LENGTH
                ? sentence.substring(0, ABSTRACT_MAX_LENGTH)
                : sentence;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
