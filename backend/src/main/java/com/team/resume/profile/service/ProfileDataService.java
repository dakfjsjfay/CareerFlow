package com.team.resume.profile.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.entity.ProfileExperience;
import com.team.resume.profile.entity.ProfileProject;
import com.team.resume.profile.entity.UserProfile;
import com.team.resume.profile.mapper.ProfileExperienceMapper;
import com.team.resume.profile.mapper.ProfileProjectMapper;
import com.team.resume.profile.mapper.UserProfileMapper;
import com.team.resume.profile.support.FlexibleDateParser;
import com.team.resume.profile.support.JsonValues;

/**
 * 档案实体写入：只在证据被用户 CONFIRMED 后调用。
 * 所有方法都假定调用方已校验 user_id 归属，并在同一事务中执行。
 */
@Service
public class ProfileDataService {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}");

    private final UserProfileMapper userProfileMapper;
    private final ProfileProjectMapper projectMapper;
    private final ProfileExperienceMapper experienceMapper;
    private final ObjectMapper objectMapper;

    public ProfileDataService(UserProfileMapper userProfileMapper,
                              ProfileProjectMapper projectMapper,
                              ProfileExperienceMapper experienceMapper,
                              ObjectMapper objectMapper) {
        this.userProfileMapper = userProfileMapper;
        this.projectMapper = projectMapper;
        this.experienceMapper = experienceMapper;
        this.objectMapper = objectMapper;
    }

    /** 获取或创建用户档案（user_profile.user_id 唯一）。 */
    public UserProfile ensureProfile(Long userId) {
        UserProfile profile = findByUserId(userId);
        if (profile != null) {
            return profile;
        }
        profile = new UserProfile();
        profile.setUserId(userId);
        try {
            userProfileMapper.insert(profile);
            return profile;
        } catch (DuplicateKeyException e) {
            // 并发确认时另一个事务已创建档案，重新读取即可
            UserProfile existing = findByUserId(userId);
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    /**
     * 写入一个基础/教育/求职意向字段。
     *
     * @return true 表示确实写入了字段
     */
    public boolean applyProfileField(UserProfile profile, String fieldName, String value) {
        String text = value == null ? null : value.trim();
        if (text == null || text.isEmpty()) {
            return false;
        }
        switch (fieldName) {
            case ExtractionEvidenceFactory.FIELD_REAL_NAME -> profile.setRealName(text);
            case ExtractionEvidenceFactory.FIELD_PHONE -> profile.setPhone(text);
            case ExtractionEvidenceFactory.FIELD_SCHOOL -> profile.setSchool(text);
            case ExtractionEvidenceFactory.FIELD_MAJOR -> profile.setMajor(text);
            case ExtractionEvidenceFactory.FIELD_DEGREE -> profile.setDegree(text);
            case ExtractionEvidenceFactory.FIELD_GRADUATION_YEAR -> {
                Integer year = parseYear(text);
                if (year == null) {
                    return false;
                }
                profile.setGraduationYear(year);
            }
            case ExtractionEvidenceFactory.FIELD_TARGET_POSITION -> profile.setTargetPosition(text);
            case ExtractionEvidenceFactory.FIELD_TARGET_CITY -> profile.setTargetCity(text);
            case ExtractionEvidenceFactory.FIELD_EXPECTED_SALARY -> profile.setExpectedSalary(text);
            default -> throw new BusinessException(400, "不支持的档案字段：" + fieldName);
        }
        return true;
    }

    /**
     * 追加技能（去重，忽略大小写）。
     *
     * @return true 表示技能是新增加的
     */
    public boolean addSkill(UserProfile profile, String skill) {
        String text = skill == null ? null : skill.trim();
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (profile.getSkills() == null) {
            profile.setSkills(new ArrayList<>());
        }
        boolean exists = profile.getSkills().stream().anyMatch(s -> s.equalsIgnoreCase(text));
        if (exists) {
            return false;
        }
        profile.getSkills().add(text);
        return true;
    }

    /** 根据证据 JSON 创建项目实体。 */
    public ProfileProject createProject(Long profileId, String evidenceContent) {
        JsonNode data = readObject(evidenceContent, "项目");
        String name = JsonValues.text(data, "projectName", "project_name", "name");
        if (name == null) {
            throw new BusinessException(400, "项目证据缺少项目名称，无法写入档案");
        }
        ProfileProject project = new ProfileProject();
        project.setProfileId(profileId);
        project.setProjectName(name);
        project.setRole(JsonValues.text(data, "role"));
        project.setDescription(JsonValues.text(data, "description", "desc"));
        List<String> techStack = JsonValues.stringList(data, "techStack", "tech_stack");
        if (!techStack.isEmpty()) {
            project.setTechStack(techStack);
        }
        project.setStartDate(FlexibleDateParser.parse(JsonValues.text(data, "startDate", "start_date")));
        project.setEndDate(FlexibleDateParser.parse(JsonValues.text(data, "endDate", "end_date")));
        projectMapper.insert(project);
        return project;
    }

    /** 根据证据 JSON 创建工作/实习经历实体。 */
    public ProfileExperience createExperience(Long profileId, String evidenceContent) {
        JsonNode data = readObject(evidenceContent, "工作/实习经历");
        String company = JsonValues.text(data, "company", "companyName", "company_name");
        if (company == null) {
            throw new BusinessException(400, "工作/实习经历证据缺少公司名称，无法写入档案");
        }
        ProfileExperience experience = new ProfileExperience();
        experience.setProfileId(profileId);
        experience.setCompany(company);
        experience.setPosition(JsonValues.text(data, "position", "jobTitle", "title"));
        experience.setDescription(JsonValues.text(data, "description", "desc"));
        experience.setAchievements(JsonValues.text(data, "achievements", "achievement", "results"));
        experience.setStartDate(FlexibleDateParser.parse(
                JsonValues.text(data, "startDate", "start_date")));
        experience.setEndDate(FlexibleDateParser.parse(
                JsonValues.text(data, "endDate", "end_date")));
        experienceMapper.insert(experience);
        return experience;
    }

    private UserProfile findByUserId(Long userId) {
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
    }

    private JsonNode readObject(String content, String label) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(400, label + "证据内容为空");
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            if (node == null || !node.isObject()) {
                throw new BusinessException(400, label + "证据内容格式错误");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new BusinessException(400, label + "证据内容格式错误");
        }
    }

    private static Integer parseYear(String raw) {
        Matcher matcher = YEAR_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group());
    }
}
