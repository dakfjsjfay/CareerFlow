package com.team.resume.profile.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.support.FactGuard;
import com.team.resume.profile.support.JsonValues;

/**
 * 把 AI 的 extractedInfo 拆成「一条信息一条证据」的 PENDING 记录。
 * <p>
 * 粒度设计（与确认接口的写入逻辑对应）：
 * - 基础信息/教育经历/求职意向：一个字段一条证据（entity_type=PROFILE，field_name 为档案字段名）；
 * - 技能：一个技能一条证据（entity_type=SKILL，field_name=skills）；
 * - 项目/工作经历：一条经历一条证据（entity_type=PROJECT/EXPERIENCE），
 *   evidence_content 保存该条经历的 JSON，确认时据此创建实体并回填 entity_id。
 * 这样确认一条项目证据就创建一条完整项目，避免 project_name NOT NULL 字段被拆散。
 * <p>
 * 这里只做宽松的结构整理，不做任何补全：抽取结果必须能在用户原话中找到依据
 * （Prompt 已约束，原文通过 source_id 追溯）。
 */
@Component
public class ExtractionEvidenceFactory {

    public static final String FIELD_REAL_NAME = "real_name";
    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_SCHOOL = "school";
    public static final String FIELD_MAJOR = "major";
    public static final String FIELD_DEGREE = "degree";
    public static final String FIELD_GRADUATION_YEAR = "graduation_year";
    public static final String FIELD_SKILLS = "skills";
    public static final String FIELD_PROJECT = "project";
    public static final String FIELD_EXPERIENCE = "experience";
    public static final String FIELD_TARGET_POSITION = "target_position";
    public static final String FIELD_TARGET_CITY = "target_city";
    public static final String FIELD_EXPECTED_SALARY = "expected_salary";

    /** 单条消息最多落多少条证据，防止异常输出撑爆确认列表。 */
    private static final int MAX_EVIDENCE_PER_MESSAGE = 100;

    public List<ProfileEvidence> build(Long userId, Long sourceMessageId,
                                       JsonNode extractedInfo, String sourceText) {
        List<ProfileEvidence> result = new ArrayList<>();
        if (userId == null || sourceMessageId == null
                || extractedInfo == null || !extractedInfo.isObject()) {
            return result;
        }

        JsonNode basic = JsonValues.object(extractedInfo, "basic");
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_REAL_NAME,
                first(JsonValues.text(basic, "realName", "real_name", "name"),
                        JsonValues.text(extractedInfo, "realName", "real_name")));
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_PHONE,
                first(JsonValues.text(basic, "phone", "mobile"),
                        JsonValues.text(extractedInfo, "phone", "mobile")));

        JsonNode education = JsonValues.object(extractedInfo, "education");
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_SCHOOL,
                first(JsonValues.text(education, "school", "university", "college"),
                        JsonValues.text(extractedInfo, "school")));
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_MAJOR,
                first(JsonValues.text(education, "major"),
                        JsonValues.text(extractedInfo, "major")));
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_DEGREE,
                first(JsonValues.text(education, "degree"),
                        JsonValues.text(extractedInfo, "degree")));
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_GRADUATION_YEAR,
                first(JsonValues.text(education, "graduationYear", "graduation_year", "graduateYear"),
                        JsonValues.text(extractedInfo, "graduationYear", "graduation_year")));

        addSkills(result, userId, sourceMessageId, sourceText, extractedInfo);
        addProjects(result, userId, sourceMessageId, sourceText, extractedInfo);
        addExperiences(result, userId, sourceMessageId, sourceText, extractedInfo);

        JsonNode intention = JsonValues.object(extractedInfo, "jobIntention", "job_intention", "jobIntent");
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_TARGET_POSITION,
                first(JsonValues.text(intention, "targetPosition", "target_position", "position"),
                        JsonValues.text(extractedInfo, "targetPosition", "target_position")));
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_TARGET_CITY,
                first(JsonValues.text(intention, "targetCity", "target_city", "city"),
                        JsonValues.text(extractedInfo, "targetCity", "target_city")));
        addProfile(result, userId, sourceMessageId, sourceText, FIELD_EXPECTED_SALARY,
                first(JsonValues.text(intention, "expectedSalary", "expected_salary", "salary"),
                        JsonValues.text(extractedInfo, "expectedSalary", "expected_salary")));

        return result;
    }

    private void addSkills(List<ProfileEvidence> result, Long userId, Long sourceMessageId,
                           String sourceText, JsonNode extractedInfo) {
        Set<String> skills = new LinkedHashSet<>(JsonValues.stringList(extractedInfo, "skills"));
        for (String skill : skills) {
            if (result.size() >= MAX_EVIDENCE_PER_MESSAGE) {
                return;
            }
            // 技能必须能在用户原话中找到依据，防止模型把示例或推测当成技能
            if (!FactGuard.isGrounded(sourceText, skill)) {
                continue;
            }
            result.add(evidence(userId, sourceMessageId,
                    ProfileEvidence.ENTITY_SKILL, FIELD_SKILLS, skill));
        }
    }

    private void addProjects(List<ProfileEvidence> result, Long userId, Long sourceMessageId,
                             String sourceText, JsonNode extractedInfo) {
        JsonNode projects = JsonValues.array(extractedInfo, "projects");
        if (projects == null) {
            return;
        }
        for (JsonNode item : projects) {
            if (result.size() >= MAX_EVIDENCE_PER_MESSAGE) {
                return;
            }
            if (!item.isObject()) {
                continue;
            }
            String name = JsonValues.text(item, "projectName", "project_name", "name");
            // 没有项目名称无法创建实体；项目名称必须能在原话中找到，防止编造项目
            if (name == null || !FactGuard.isGrounded(sourceText, name)) {
                continue;
            }
            ObjectNode data = JsonNodeFactory.instance.objectNode();
            data.put("projectName", name);
            putIfPresent(data, "role", JsonValues.text(item, "role"));
            putIfPresent(data, "description",
                    JsonValues.text(item, "description", "desc", "summary"));
            // 技术栈逐项校验：只能说用户明确说过的技术
            List<String> techStack = JsonValues.stringList(item, "techStack", "tech_stack", "stack")
                    .stream()
                    .filter(tech -> FactGuard.isGrounded(sourceText, tech))
                    .toList();
            if (!techStack.isEmpty()) {
                ArrayNode array = data.putArray("techStack");
                techStack.forEach(array::add);
            }
            putIfPresent(data, "startDate", JsonValues.text(item, "startDate", "start_date"));
            putIfPresent(data, "endDate", JsonValues.text(item, "endDate", "end_date"));
            result.add(evidence(userId, sourceMessageId,
                    ProfileEvidence.ENTITY_PROJECT, FIELD_PROJECT, data.toString()));
        }
    }

    private void addExperiences(List<ProfileEvidence> result, Long userId, Long sourceMessageId,
                                String sourceText, JsonNode extractedInfo) {
        JsonNode experiences = JsonValues.array(extractedInfo, "experiences");
        if (experiences == null) {
            return;
        }
        for (JsonNode item : experiences) {
            if (result.size() >= MAX_EVIDENCE_PER_MESSAGE) {
                return;
            }
            if (!item.isObject()) {
                continue;
            }
            String company = JsonValues.text(item, "company", "companyName", "company_name");
            // 公司名称必须能在原话中找到，防止编造经历
            if (company == null || !FactGuard.isGrounded(sourceText, company)) {
                continue;
            }
            ObjectNode data = JsonNodeFactory.instance.objectNode();
            data.put("company", company);
            putIfPresent(data, "position",
                    JsonValues.text(item, "position", "jobTitle", "job_title", "title"));
            putIfPresent(data, "description",
                    JsonValues.text(item, "description", "desc", "summary"));
            putIfPresent(data, "achievements",
                    JsonValues.text(item, "achievements", "achievement", "results"));
            putIfPresent(data, "startDate", JsonValues.text(item, "startDate", "start_date"));
            putIfPresent(data, "endDate", JsonValues.text(item, "endDate", "end_date"));
            result.add(evidence(userId, sourceMessageId,
                    ProfileEvidence.ENTITY_EXPERIENCE, FIELD_EXPERIENCE, data.toString()));
        }
    }

    private void addProfile(List<ProfileEvidence> result, Long userId, Long sourceMessageId,
                            String sourceText, String fieldName, String value) {
        if (result.size() >= MAX_EVIDENCE_PER_MESSAGE || value == null || value.isBlank()) {
            return;
        }
        // 基础信息/教育/求职意向同样必须能在原话中找到依据
        if (!FactGuard.isGrounded(sourceText, value)) {
            return;
        }
        result.add(evidence(userId, sourceMessageId,
                ProfileEvidence.ENTITY_PROFILE, fieldName, value.trim()));
    }

    private static ProfileEvidence evidence(Long userId, Long sourceMessageId,
                                            String entityType, String fieldName, String content) {
        ProfileEvidence evidence = new ProfileEvidence();
        evidence.setUserId(userId);
        evidence.setEntityType(entityType);
        evidence.setFieldName(fieldName);
        evidence.setSourceType(ProfileEvidence.SOURCE_CHAT);
        evidence.setSourceId(sourceMessageId);
        evidence.setEvidenceContent(content);
        evidence.setConfirmationStatus(ProfileEvidence.STATUS_PENDING);
        return evidence;
    }

    private static void putIfPresent(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value.trim());
        }
    }

    private static String first(String primary, String fallback) {
        return primary != null ? primary : fallback;
    }
}
