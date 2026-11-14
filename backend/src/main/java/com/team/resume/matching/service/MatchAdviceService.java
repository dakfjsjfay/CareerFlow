package com.team.resume.matching.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.ai.AIService;
import com.team.resume.common.BusinessException;
import com.team.resume.job.entity.Job;
import com.team.resume.job.entity.JobRequirement;
import com.team.resume.job.mapper.JobMapper;
import com.team.resume.job.mapper.JobRequirementMapper;
import com.team.resume.matching.dto.AdviceRequest;
import com.team.resume.matching.service.MatchCalculator.Result;
import com.team.resume.matching.service.MatchCalculator.SkillMatch;
import com.team.resume.matching.vo.AdviceVO;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileEvidenceMapper;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.service.ProfileDataService;
import com.team.resume.resume.entity.Resume;
import com.team.resume.resume.entity.ResumeVersion;
import com.team.resume.resume.mapper.ResumeMapper;
import com.team.resume.resume.mapper.ResumeVersionMapper;

/**
 * 投递建议（docs/api.md §9.2，赛题要求的「匹配度分析 + 投递话术」）：
 * - 分数与逐技能结论由 Java 复用 MatchCalculator 计算，不落库（MVP 实时生成）
 * - AI 只负责生成 pitchMessage
 * - 话术生成后做防编造校验：一旦出现 MISSING / UNCONFIRMED 技能，直接回退确定性话术
 */
@Service
public class MatchAdviceService {

    private static final Logger log = LoggerFactory.getLogger(MatchAdviceService.class);
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}+#]");
    private static final int MAX_PITCH_LENGTH = 600;

    private final JobMapper jobMapper;
    private final JobRequirementMapper requirementMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ProfileDataService profileDataService;
    private final ProfileProjectMapper projectMapper;
    private final ProfileExperienceMapper experienceMapper;
    private final ProfileEvidenceMapper evidenceMapper;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    public MatchAdviceService(JobMapper jobMapper,
                              JobRequirementMapper requirementMapper,
                              ResumeMapper resumeMapper,
                              ResumeVersionMapper resumeVersionMapper,
                              ProfileDataService profileDataService,
                              ProfileProjectMapper projectMapper,
                              ProfileExperienceMapper experienceMapper,
                              ProfileEvidenceMapper evidenceMapper,
                              AIService aiService,
                              ObjectMapper objectMapper) {
        this.jobMapper = jobMapper;
        this.requirementMapper = requirementMapper;
        this.resumeMapper = resumeMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.profileDataService = profileDataService;
        this.projectMapper = projectMapper;
        this.experienceMapper = experienceMapper;
        this.evidenceMapper = evidenceMapper;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    public AdviceVO advice(Long userId, AdviceRequest request) {
        Job job = requireJob(request.getJobId());
        requireOwnedVersion(userId, request.getResumeVersionId());

        List<JobRequirement> requirements = requirementMapper.selectList(
                new LambdaQueryWrapper<JobRequirement>()
                        .eq(JobRequirement::getJobId, job.getId())
                        .orderByDesc(JobRequirement::getWeight));
        if (requirements.isEmpty()) {
            throw new BusinessException(400, "该岗位还没有技能要求，请先解析 JD");
        }

        UserProfile profile = profileDataService.findByUserId(userId);
        List<String> skills = profile != null && profile.getSkills() != null
                ? profile.getSkills() : List.of();
        List<ProfileProject> projects = profile != null
                ? projectMapper.selectList(new LambdaQueryWrapper<ProfileProject>()
                        .eq(ProfileProject::getProfileId, profile.getId()))
                : List.of();
        List<ProfileExperience> experiences = profile != null
                ? experienceMapper.selectList(new LambdaQueryWrapper<ProfileExperience>()
                        .eq(ProfileExperience::getProfileId, profile.getId()))
                : List.of();
        List<ProfileEvidence> pending = evidenceMapper.selectList(
                new LambdaQueryWrapper<ProfileEvidence>()
                        .eq(ProfileEvidence::getUserId, userId)
                        .eq(ProfileEvidence::getConfirmationStatus, "PENDING"));

        Result calculated = MatchCalculator.calculate(requirements, skills, projects, pending);

        List<String> strengths = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();
        List<String> unconfirmedSkills = new ArrayList<>();
        for (SkillMatch detail : calculated.details()) {
            switch (detail.status()) {
                case MatchCalculator.MATCHED -> strengths.add(detail.skill() + " 有项目证据"
                        + (detail.evidence() == null ? "" : "（" + detail.evidence() + "）"));
                case MatchCalculator.PARTIAL -> {
                    strengths.add(detail.skill() + " 有学习记录");
                    suggestions.add("建议补充「" + detail.skill() + "」在项目中的应用证据");
                }
                case MatchCalculator.UNCONFIRMED -> {
                    gaps.add(detail.skill() + " 信息待确认");
                    unconfirmedSkills.add(detail.skill());
                    suggestions.add("请先在档案页确认「" + detail.skill() + "」相关信息");
                }
                default -> {
                    gaps.add(detail.skill() + " 无证据");
                    missingSkills.add(detail.skill());
                    suggestions.add("建议先补充「" + detail.skill() + "」的真实练习或项目经历后再投递");
                }
            }
        }
        if (gaps.isEmpty()) {
            suggestions.add("技能覆盖良好，可以直接投递");
        }

        String factsJson = buildFactsJson(profile, skills, projects, experiences, job, calculated);
        String pitch = generatePitch(factsJson, profile, skills, projects, job, calculated,
                missingSkills, unconfirmedSkills);

        AdviceVO vo = new AdviceVO();
        vo.setMatchScore(calculated.score());
        vo.setStrengths(strengths);
        vo.setGaps(gaps);
        vo.setPitchMessage(pitch);
        vo.setSuggestions(suggestions);
        return vo;
    }

    /**
     * AI 失败或话术提到缺失/待确认技能时，回退到完全由已确认事实拼装的确定性话术。
     */
    private String generatePitch(String factsJson, UserProfile profile, List<String> skills,
                                 List<ProfileProject> projects, Job job, Result calculated,
                                 List<String> missingSkills, List<String> unconfirmedSkills) {
        String pitch;
        try {
            pitch = aiService.generateAdvicePitch(factsJson);
        } catch (RuntimeException e) {
            log.warn("AI 生成投递话术失败（job={}），回退确定性话术", job.getId(), e);
            return deterministicPitch(profile, skills, projects, job, calculated);
        }

        if (pitch == null || pitch.isBlank()) {
            return deterministicPitch(profile, skills, projects, job, calculated);
        }
        String trimmed = pitch.trim();
        if (trimmed.length() > MAX_PITCH_LENGTH
                || mentionsAny(trimmed, missingSkills)
                || mentionsAny(trimmed, unconfirmedSkills)) {
            log.warn("AI 话术包含未确认技能或长度异常（job={}），回退确定性话术", job.getId());
            return deterministicPitch(profile, skills, projects, job, calculated);
        }
        return trimmed;
    }

    private String deterministicPitch(UserProfile profile, List<String> skills,
                                      List<ProfileProject> projects, Job job, Result calculated) {
        StringBuilder sb = new StringBuilder("您好，我是");
        List<String> schoolParts = new ArrayList<>();
        if (profile != null && hasText(profile.getSchool())) {
            schoolParts.add(profile.getSchool().trim());
        }
        if (profile != null && hasText(profile.getMajor())) {
            schoolParts.add(profile.getMajor().trim() + "专业");
        }
        if (profile != null && hasText(profile.getDegree())) {
            schoolParts.add(profile.getDegree().trim());
        }
        sb.append(schoolParts.isEmpty() ? "一名求职者" : String.join("", schoolParts) + "学生");
        if (profile != null && hasText(profile.getRealName())) {
            sb.append("（").append(profile.getRealName().trim()).append("）");
        }

        List<String> masteredSkills = calculated.details().stream()
                .filter(d -> MatchCalculator.MATCHED.equals(d.status()))
                .map(SkillMatch::skill)
                .toList();
        List<String> learnedSkills = calculated.details().stream()
                .filter(d -> MatchCalculator.PARTIAL.equals(d.status()))
                .map(SkillMatch::skill)
                .toList();
        if (!masteredSkills.isEmpty()) {
            sb.append("，已掌握").append(String.join("、", masteredSkills));
        }
        if (!learnedSkills.isEmpty()) {
            sb.append("，学习过").append(String.join("、", learnedSkills));
        }
        if (!projects.isEmpty()) {
            ProfileProject first = projects.get(0);
            sb.append("，并完成过「").append(first.getProjectName()).append("」");
            if (hasText(first.getRole())) {
                sb.append("（").append(first.getRole().trim()).append("）");
            }
        } else if (!skills.isEmpty()) {
            sb.append("，学习过").append(String.join("、", skills));
        }
        sb.append("。关注到贵司「").append(job.getPosition()).append("」岗位，希望有机会进一步交流。");
        return sb.toString();
    }

    private String buildFactsJson(UserProfile profile, List<String> skills,
                                  List<ProfileProject> projects,
                                  List<ProfileExperience> experiences,
                                  Job job, Result calculated) {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode personal = root.putObject("personalInfo");
        if (profile != null) {
            putText(personal, "realName", profile.getRealName());
            putText(personal, "school", profile.getSchool());
            putText(personal, "major", profile.getMajor());
            putText(personal, "degree", profile.getDegree());
            if (profile.getGraduationYear() != null) {
                personal.put("graduationYear", profile.getGraduationYear());
            }
            putText(personal, "targetPosition", profile.getTargetPosition());
            putText(personal, "targetCity", profile.getTargetCity());
            putText(personal, "expectedSalary", profile.getExpectedSalary());
        }

        ArrayNode skillArray = root.putArray("skills");
        skills.forEach(skillArray::add);

        ArrayNode projectArray = root.putArray("projects");
        for (ProfileProject project : projects) {
            ObjectNode item = projectArray.addObject();
            putText(item, "projectName", project.getProjectName());
            putText(item, "role", project.getRole());
            putText(item, "description", project.getDescription());
            ArrayNode tech = item.putArray("techStack");
            if (project.getTechStack() != null) {
                project.getTechStack().forEach(tech::add);
            }
            if (project.getStartDate() != null) {
                item.put("startDate", project.getStartDate().toString());
            }
            if (project.getEndDate() != null) {
                item.put("endDate", project.getEndDate().toString());
            }
        }

        ArrayNode experienceArray = root.putArray("experiences");
        for (ProfileExperience experience : experiences) {
            ObjectNode item = experienceArray.addObject();
            putText(item, "company", experience.getCompany());
            putText(item, "position", experience.getPosition());
            putText(item, "description", experience.getDescription());
            putText(item, "achievements", experience.getAchievements());
        }

        ObjectNode jobNode = root.putObject("job");
        putText(jobNode, "company", job.getCompany());
        putText(jobNode, "position", job.getPosition());
        putText(jobNode, "city", job.getCity());
        putText(jobNode, "salary", job.getSalary());

        ObjectNode matchNode = root.putObject("match");
        matchNode.put("matchScore", calculated.score());
        ArrayNode detailArray = matchNode.putArray("details");
        for (SkillMatch detail : calculated.details()) {
            detailArray.addObject()
                    .put("skill", detail.skill())
                    .put("status", detail.status());
        }
        return root.toString();
    }

    private static void putText(ObjectNode node, String field, String value) {
        if (hasText(value)) {
            node.put(field, value.trim());
        }
    }

    private static boolean mentionsAny(String text, List<String> skills) {
        if (skills.isEmpty()) {
            return false;
        }
        String normalized = normalize(text);
        return skills.stream()
                .filter(MatchAdviceService::hasText)
                .anyMatch(skill -> normalized.contains(normalize(skill)));
    }

    private static String normalize(String text) {
        return NON_WORD.matcher(text).replaceAll("").toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Job requireJob(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return job;
    }

    private ResumeVersion requireOwnedVersion(Long userId, Long versionId) {
        ResumeVersion version = resumeVersionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        Resume resume = resumeMapper.selectById(version.getResumeId());
        if (resume == null || !Objects.equals(resume.getUserId(), userId)) {
            throw new BusinessException(403, "该简历版本不属于当前用户");
        }
        return version;
    }
}
