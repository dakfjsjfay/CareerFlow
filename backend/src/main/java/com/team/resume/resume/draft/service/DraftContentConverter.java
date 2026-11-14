package com.team.resume.resume.draft.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.common.BusinessException;
import com.team.resume.resume.vo.ResumeContent;

/**
 * 八步草稿 JSON → 结构化简历内容的映射器。
 * <p>
 * 约定：草稿 content 是可编辑的宽松结构（含来源标识），正式版本 content 必须符合
 * {@link ResumeContent}；版本额外写入 sourceTrace，保证每条经历可追溯到原始来源。
 */
@Component
public class DraftContentConverter {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    public DraftContentConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 第八步生成前的硬校验：基础信息必须完整。 */
    public void validateForComplete(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new BusinessException(400, "草稿内容为空，无法生成简历");
        }
        String realName = text(content.path("basic").path("realName"));
        String phone = text(content.path("basic").path("phone"));
        String email = text(content.path("basic").path("email"));
        if (realName.isBlank()) {
            throw new BusinessException(400, "请先填写姓名");
        }
        if (phone.isBlank() && email.isBlank()) {
            throw new BusinessException(400, "请至少填写手机号或邮箱");
        }
    }

    /**
     * 把草稿内容转换为正式版本内容；templateId 优先取草稿预览设置，其次取请求参数。
     */
    public ResumeContent toResumeContent(JsonNode content, String templateId, String generationReason) {
        JsonNode basic = content.path("basic");
        JsonNode direction = content.path("direction");
        String selfEvaluation = text(content.path("soft").path("selfEvaluation"));
        if (selfEvaluation.isBlank()) {
            selfEvaluation = join(content.path("soft").path("strengths"), "；");
        }

        String resolvedTemplateId = text(content.path("preview").path("templateId"));
        if (resolvedTemplateId.isBlank()) {
            resolvedTemplateId = templateId;
        }

        ResumeContent.Meta meta = new ResumeContent.Meta(
                blankToNull(resolvedTemplateId),
                blankToNull(text(content.path("preview").path("templateName"))),
                LocalDateTime.now().format(DATE_TIME),
                "WIZARD",
                generationReason == null || generationReason.isBlank() ? "INITIAL" : generationReason,
                null,
                List.of());

        ResumeContent.PersonalInfo personalInfo = new ResumeContent.PersonalInfo(
                blankToNull(text(basic.path("realName"))),
                blankToNull(text(basic.path("phone"))),
                blankToNull(text(basic.path("email"))),
                blankToNull(text(basic.path("city"))),
                blankToNull(text(content.path("photo").path("dataUrl"))),
                blankToNull(text(basic.path("school"))),
                blankToNull(text(basic.path("major"))),
                blankToNull(text(basic.path("degree"))),
                integerOrNull(basic.path("graduationYear")),
                null,
                blankToNull(truncate(selfEvaluation, 60)));

        ResumeContent.JobIntention jobIntention = new ResumeContent.JobIntention(
                blankToNull(text(direction.path("targetPosition"))),
                blankToNull(text(direction.path("targetCity"))),
                blankToNull(text(direction.path("expectedSalary"))));

        return new ResumeContent(
                meta,
                personalInfo,
                jobIntention,
                toEducation(content),
                toStringList(content.path("skills")),
                toProjects(content.path("projects")),
                toExperiences(content.path("experiences")),
                selfEvaluation);
    }

    private List<ResumeContent.Education> toEducation(JsonNode content) {
        List<ResumeContent.Education> result = new ArrayList<>();
        for (JsonNode item : content.path("educations")) {
            if (text(item.path("school")).isBlank()) {
                continue;
            }
            result.add(new ResumeContent.Education(
                    blankToNull(text(item.path("school"))),
                    blankToNull(text(item.path("major"))),
                    blankToNull(text(item.path("degree"))),
                    integerOrNull(item.path("graduationYear")),
                    parseDate(text(item.path("startDate"))),
                    parseDate(text(item.path("endDate")))));
        }
        if (result.isEmpty()) {
            JsonNode basic = content.path("basic");
            if (!text(basic.path("school")).isBlank()) {
                result.add(new ResumeContent.Education(
                        text(basic.path("school")),
                        blankToNull(text(basic.path("major"))),
                        blankToNull(text(basic.path("degree"))),
                        integerOrNull(basic.path("graduationYear")),
                        null,
                        null));
            }
        }
        return result;
    }

    private List<ResumeContent.Project> toProjects(JsonNode projects) {
        List<ResumeContent.Project> result = new ArrayList<>();
        for (JsonNode item : projects) {
            String name = text(item.path("projectName"));
            if (name.isBlank()) {
                continue;
            }
            String description = text(item.path("description"));
            if (description.isBlank()) {
                description = starText(item.path("star"));
            }
            result.add(new ResumeContent.Project(
                    name,
                    blankToNull(text(item.path("role"))),
                    blankToNull(description),
                    toStringList(item.path("techStack")),
                    toStringList(item.path("highlights")),
                    parseDate(text(item.path("startDate"))),
                    parseDate(text(item.path("endDate")))));
        }
        return result;
    }

    private List<ResumeContent.Experience> toExperiences(JsonNode experiences) {
        List<ResumeContent.Experience> result = new ArrayList<>();
        for (JsonNode item : experiences) {
            String company = text(item.path("company"));
            String position = text(item.path("position"));
            if (company.isBlank() && position.isBlank()) {
                continue;
            }
            String description = text(item.path("description"));
            if (description.isBlank()) {
                description = starText(item.path("star"));
            }
            result.add(new ResumeContent.Experience(
                    blankToNull(company),
                    blankToNull(position),
                    "INTERNSHIP".equals(text(item.path("kind"))) ? "INTERNSHIP" : "WORK",
                    blankToNull(description),
                    blankToNull(text(item.path("achievements"))),
                    List.of(),
                    parseDate(text(item.path("startDate"))),
                    parseDate(text(item.path("endDate")))));
        }
        return result;
    }

    /**
     * 版本内容 = 结构化简历 + 来源追溯链。读取方按 ResumeContent 反序列化时忽略 sourceTrace。
     */
    public JsonNode buildVersionContent(ResumeContent resumeContent, JsonNode draftContent) {
        ObjectNode node = objectMapper.valueToTree(resumeContent);
        node.set("sourceTrace", buildSourceTrace(draftContent));
        return node;
    }

    /** 每条经历的字段级来源标识，便于追溯至档案或用户手工输入。 */
    public JsonNode buildSourceTrace(JsonNode draftContent) {
        ObjectNode trace = objectMapper.createObjectNode();
        trace.put("schemaVersion", 1);
        trace.set("educations", traceItems(draftContent.path("educations"), List.of(
                "school", "major", "degree", "graduationYear", "startDate", "endDate")));
        trace.set("experiences", traceItems(draftContent.path("experiences"), List.of(
                "company", "position", "kind", "startDate", "endDate", "description", "achievements")));
        trace.set("projects", traceItems(draftContent.path("projects"), List.of(
                "projectName", "role", "description", "startDate", "endDate")));
        trace.set("skills", traceItems(draftContent.path("skills"), List.of()));
        return trace;
    }

    private ArrayNode traceItems(JsonNode items, List<String> fields) {
        ArrayNode array = objectMapper.createArrayNode();
        for (JsonNode item : items) {
            ObjectNode entry = objectMapper.createObjectNode();
            if (item.hasNonNull("id")) {
                entry.put("id", item.path("id").asText());
            }
            entry.set("fieldSources", item.path("fieldSources").isObject()
                    ? item.path("fieldSources").deepCopy()
                    : objectMapper.createObjectNode());
            if (fields.isEmpty() && item.isTextual()) {
                entry.put("value", item.asText());
            }
            array.add(entry);
        }
        return array;
    }

    /** 取草稿中的某个区块，缺失时返回空数组或空对象。 */
    public JsonNode section(JsonNode content, String sectionKey) {
        if (content == null) {
            return objectMapper.createObjectNode();
        }
        String field = switch (sectionKey) {
            case "BASIC" -> "basic";
            case "EDUCATION" -> "educations";
            case "PROJECT" -> "projects";
            case "EXPERIENCE" -> "experiences";
            case "SKILL" -> "skills";
            case "SELF_EVALUATION", "SOFT" -> "soft";
            case "PHOTO" -> "photo";
            default -> throw new BusinessException(400, "不支持的区块：" + sectionKey);
        };
        JsonNode node = content.path(field);
        return node.isMissingNode() || node.isNull() ? objectMapper.createObjectNode() : node;
    }

    /** 把用户确认后的候选内容写回草稿区块。 */
    public void applySection(ObjectNode content, String sectionKey, JsonNode afterContent) {
        String field = switch (sectionKey) {
            case "BASIC" -> "basic";
            case "EDUCATION" -> "educations";
            case "PROJECT" -> "projects";
            case "EXPERIENCE" -> "experiences";
            case "SKILL" -> "skills";
            case "SELF_EVALUATION", "SOFT" -> "soft";
            case "PHOTO" -> "photo";
            default -> throw new BusinessException(400, "不支持的区块：" + sectionKey);
        };
        content.set(field, afterContent);
    }

    /**
     * 单条经历/项目的候选写回：按 id 替换数组中的对应元素，找不到时追加。
     */
    public void applyItem(ObjectNode content, String sectionKey, JsonNode afterItem) {
        String field = switch (sectionKey) {
            case "EXPERIENCE" -> "experiences";
            case "PROJECT" -> "projects";
            case "EDUCATION" -> "educations";
            default -> throw new BusinessException(400, "不支持的条目区块：" + sectionKey);
        };
        ArrayNode array = content.has(field) && content.get(field).isArray()
                ? (ArrayNode) content.get(field)
                : objectMapper.createArrayNode();
        String id = text(afterItem.path("id"));
        boolean replaced = false;
        for (int i = 0; i < array.size(); i++) {
            if (!id.isBlank() && id.equals(text(array.get(i).path("id")))) {
                array.set(i, afterItem);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            array.add(afterItem);
        }
        content.set(field, array);
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    private static String starText(JsonNode star) {
        Map<String, String> parts = new LinkedHashMap<>();
        parts.put("情境", text(star.path("situation")));
        parts.put("任务", text(star.path("task")));
        parts.put("行动", text(star.path("action")));
        parts.put("结果", text(star.path("result")));
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : parts.entrySet()) {
            if (!entry.getValue().isBlank()) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(entry.getKey()).append("：").append(entry.getValue());
            }
        }
        return builder.toString();
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode item : array) {
            String value = item.isTextual() ? item.asText().trim() : text(item.path("value"));
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private static String join(JsonNode array, String separator) {
        return String.join(separator, toStringList(array));
    }

    public static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
    }

    private static Integer integerOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.asInt();
        }
        String value = node.asText("").trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.replaceAll("\\D", "").isEmpty() ? value : value.replaceAll("\\D", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 支持 "2023-09"、"2023/09"、"2023-09-01"、"2023年9月"、"2023" 等输入。 */
    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace("年", "-").replace("月", "").replace("日", "")
                .replace('/', '-').replace('.', '-');
        String[] parts = value.split("-");
        try {
            int year = Integer.parseInt(parts[0].replaceAll("\\D", ""));
            int month = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("\\D", "")) : 1;
            int day = parts.length > 2 ? Integer.parseInt(parts[2].replaceAll("\\D", "")) : 1;
            return LocalDate.of(year, Math.min(Math.max(month, 1), 12), Math.min(Math.max(day, 1), 28));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
