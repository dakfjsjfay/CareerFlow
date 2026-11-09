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
 * 来源归属：sources 只包含用户消息（按时间正序）。每个值从最近一条消息向前查找
 * 能支撑它的原话，source_id 指向该消息；找不到依据的值直接丢弃。
 * 因此模型从历史中再次抽取的信息也能正确追溯到最初说那句话的消息。
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

    public List<ProfileEvidence> build(Long userId, List<EvidenceSource> sources,
                                       JsonNode extractedInfo) {
        List<ProfileEvidence> result = new ArrayList<>();
        if (userId == null || sources == null || sources.isEmpty()
                || extractedInfo == null || !extractedInfo.isObject()) {
            return result;
        }

        JsonNode basic = JsonValues.object(extractedInfo, "basic");
        addProfile(result, userId, sources, FIELD_REAL_NAME,
                first(JsonValues.text(basic, "realName", "real_name", "name"),
                        JsonValues.text(extractedInfo, "realName", "real_name")));
        addProfile(result, userId, sources, FIELD_PHONE,
                first(JsonValues.text(basic, "phone", "mobile"),
                        JsonValues.text(extractedInfo, "phone", "mobile")));

        JsonNode education = JsonValues.object(extractedInfo, "education");
        addProfile(result, userId, sources, FIELD_SCHOOL,
                first(JsonValues.text(education, "school", "university", "college"),
                        JsonValues.text(extractedInfo, "school")));
        addProfile(result, userId, sources, FIELD_MAJOR,
                first(JsonValues.text(education, "major"),
                        JsonValues.text(extractedInfo, "major")));
        addProfile(result, userId, sources, FIELD_DEGREE,
                first(JsonValues.text(education, "degree"),
                        JsonValues.text(extractedInfo, "degree")));
        addProfile(result, userId, sources, FIELD_GRADUATION_YEAR,
                first(JsonValues.text(education, "graduationYear", "graduation_year", "graduateYear"),
                        JsonValues.text(extractedInfo, "graduationYear", "graduation_year")));

        addSkills(result, userId, sources, extractedInfo);
        addProjects(result, userId, sources, extractedInfo);
        addExperiences(result, userId, sources, extractedInfo);

        JsonNode intention = JsonValues.object(extractedInfo, "jobIntention", "job_intention", "jobIntent");
        addProfile(result, userId, sources, FIELD_TARGET_POSITION,
                first(JsonValues.text(intention, "targetPosition", "target_position", "position"),
                        JsonValues.text(extractedInfo, "targetPosition", "target_position")));
        addProfile(result, userId, sources, FIELD_TARGET_CITY,
                first(JsonValues.text(intention, "targetCity", "target_city", "city"),
                        JsonValues.text(extractedInfo, "targetCity", "target_city")));
        addProfile(result, userId, sources, FIELD_EXPECTED_SALARY,
                first(JsonValues.text(intention, "expectedSalary", "expected_salary", "salary"),
                        JsonValues.text(extractedInfo, "expectedSalary", "expected_salary")));

        return result;
    }

    private void addSkills(List<ProfileEvidence> result, Long userId, List<EvidenceSource> sources,
                           JsonNode extractedInfo) {
        Set<String> skills = new LinkedHashSet<>(JsonValues.stringList(extractedInfo, "skills"));
        for (String skill : skills) {
            if (result.size() >= MAX_EVIDENCE_PER_MESSAGE) {
                return;
            }
            EvidenceSource source = resolveSource(sources, skill);
            if (source == null) {
                continue;
            }
            result.add(evidence(userId, source.messageId(),
                    ProfileEvidence.ENTITY_SKILL, FIELD_SKILLS, skill));
        }
    }

    private void addProjects(List<ProfileEvidence> result, Long userId, List<EvidenceSource> sources,
                             JsonNode extractedInfo) {
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
            EvidenceSource source = name == null ? null : resolveSource(sources, name);
            if (source == null) {
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
                    .filter(tech -> resolveSource(sources, tech) != null)
                    .toList();
            if (!techStack.isEmpty()) {
                ArrayNode array = data.putArray("techStack");
                techStack.forEach(array::add);
            }
            putIfPresent(data, "startDate", JsonValues.text(item, "startDate", "start_date"));
            putIfPresent(data, "endDate", JsonValues.text(item, "endDate", "end_date"));
            result.add(evidence(userId, source.messageId(),
                    ProfileEvidence.ENTITY_PROJECT, FIELD_PROJECT, data.toString()));
        }
    }

    private void addExperiences(List<ProfileEvidence> result, Long userId,
                                List<EvidenceSource> sources, JsonNode extractedInfo) {
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
            EvidenceSource source = company == null ? null : resolveSource(sources, company);
            if (source == null) {
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
            result.add(evidence(userId, source.messageId(),
                    ProfileEvidence.ENTITY_EXPERIENCE, FIELD_EXPERIENCE, data.toString()));
        }
    }

    private void addProfile(List<ProfileEvidence> result, Long userId, List<EvidenceSource> sources,
                            String fieldName, String value) {
        if (result.size() >= MAX_EVIDENCE_PER_MESSAGE || value == null || value.isBlank()) {
            return;
        }
        EvidenceSource source = resolveSource(sources, value);
        if (source == null) {
            return;
        }
        result.add(evidence(userId, source.messageId(),
                ProfileEvidence.ENTITY_PROFILE, fieldName, value.trim()));
    }

    /** 从最近一条用户消息向前找；同一事实重复出现时取最近一次。 */
    private static EvidenceSource resolveSource(List<EvidenceSource> sources, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (int i = sources.size() - 1; i >= 0; i--) {
            EvidenceSource source = sources.get(i);
            if (source != null && FactGuard.isGrounded(source.content(), value)) {
                return source;
            }
        }
        return null;
    }

    private static ProfileEvidence evidence(Long userId, Long sourceId,
                                            String entityType, String fieldName, String content) {
        ProfileEvidence evidence = new ProfileEvidence();
        evidence.setUserId(userId);
        evidence.setEntityType(entityType);
        evidence.setFieldName(fieldName);
        evidence.setSourceType(ProfileEvidence.SOURCE_CHAT);
        evidence.setSourceId(sourceId);
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
