package com.team.resume.resume.edit.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * AI 候选内容的事实校验（硬约束：不得编造用户未提供的事实）。
 * <p>
 * 校验策略分两层：
 * 1. 事实字段（公司、学校、专业、岗位、项目名、技能、时间）：必须能在用户原文中找到，
 * 否则回退为修改前的值，并记录警告；
 * 2. 叙述字段（描述、成果、STAR 四要素）：允许专业润色，但其中出现的数字必须来自原文，
 * 否则删除包含该数字的句子并记录警告。
 */
@Component
public class CandidateFactGuard {

    private static final Set<String> FACT_FIELDS = Set.of(
            "company", "school", "major", "degree", "position", "projectName", "role",
            "startDate", "endDate", "graduationYear", "kind", "type");

    private static final Set<String> FACT_LIST_FIELDS = Set.of("techStack", "skills");

    private static final Set<String> NARRATIVE_FIELDS = Set.of(
            "description", "achievements", "highlights", "situation", "task", "action", "result",
            "selfEvaluation", "strengths", "suggestions", "jobGoal", "expectedSalary", "realName",
            "phone", "email", "city", "industry", "targetPosition", "targetCity");

    private static final Set<String> PRESERVED_FIELDS = Set.of("id", "fieldSources", "star");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?%?");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？；;])");

    private final ObjectMapper objectMapper;

    public CandidateFactGuard(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record SanitizeResult(JsonNode content, List<String> warnings) {
    }

    /**
     * @param after       AI 返回的候选内容
     * @param before      修改前的原始内容（结构参考）
     * @param sourceText  用户提供的全部原始事实文本
     */
    public SanitizeResult sanitize(JsonNode after, JsonNode before, String sourceText) {
        List<String> warnings = new ArrayList<>();
        String normalizedSource = normalize(sourceText);
        Set<String> sourceNumbers = numbersOf(sourceText);
        JsonNode cleaned = clean(after, before, normalizedSource, sourceNumbers, warnings, "");
        return new SanitizeResult(cleaned, warnings.stream().distinct().toList());
    }

    private JsonNode clean(JsonNode node, JsonNode before, String normalizedSource,
                           Set<String> sourceNumbers, List<String> warnings, String path) {
        if (node == null || node.isNull()) {
            return before != null ? before : objectMapper.nullNode();
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(
                    cleanText(node.asText(), normalizedSource, sourceNumbers, warnings, path));
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            JsonNode beforeArray = before != null && before.isArray() ? before : objectMapper.createArrayNode();
            boolean factualStrings = node.size() > 0 && node.get(0).isTextual()
                    && (path.isEmpty() || "skills".equals(path) || "techStack".equals(path));
            for (int i = 0; i < node.size(); i++) {
                JsonNode item = node.get(i);
                if (factualStrings) {
                    if (grounded(normalizedSource, item.asText())) {
                        array.add(item.asText());
                    } else {
                        warnings.add("已移除未在原始信息中出现的内容：" + item.asText());
                    }
                    continue;
                }
                String itemKey = item.isObject() && item.hasNonNull("id") ? item.path("id").asText() : null;
                JsonNode beforeItem = findByKey(beforeArray, itemKey, i);
                array.add(clean(item, beforeItem, normalizedSource, sourceNumbers, warnings, path));
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = objectMapper.createObjectNode();
            ObjectNode beforeObject = before != null && before.isObject() ? (ObjectNode) before : null;
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                JsonNode value = node.get(field);
                if (!PRESERVED_FIELDS.contains(field)
                        && beforeObject != null && !beforeObject.has(field)) {
                    warnings.add("已移除 AI 新增的字段：" + field);
                    continue;
                }
                if (FACT_FIELDS.contains(field) && value.isTextual()) {
                    if (!grounded(normalizedSource, value.asText())) {
                        JsonNode fallback = beforeObject == null ? null : beforeObject.get(field);
                        if (fallback != null && !fallback.isNull()) {
                            object.set(field, fallback);
                            warnings.add("已回退未在原始信息中出现的字段：" + field);
                        } else {
                            object.put(field, "");
                            warnings.add("已清空无法核实的字段：" + field);
                        }
                        continue;
                    }
                }
                if (FACT_LIST_FIELDS.contains(field) && value.isArray()) {
                    ArrayNode filtered = objectMapper.createArrayNode();
                    for (JsonNode item : value) {
                        if (item.isTextual() && grounded(normalizedSource, item.asText())) {
                            filtered.add(item.asText());
                        } else if (item.isTextual()) {
                            warnings.add("已移除未在原始信息中出现的技能：" + item.asText());
                        }
                    }
                    object.set(field, filtered);
                    continue;
                }
                object.set(field, clean(value, beforeObject == null ? null : beforeObject.get(field),
                        normalizedSource, sourceNumbers, warnings, field));
            }
            // 保留原结构里 AI 漏掉的字段，避免信息丢失
            if (beforeObject != null) {
                Iterator<String> beforeNames = beforeObject.fieldNames();
                while (beforeNames.hasNext()) {
                    String field = beforeNames.next();
                    if (!object.has(field)) {
                        object.set(field, beforeObject.get(field));
                    }
                }
            }
            return object;
        }
        return node;
    }

    private String cleanText(String text, String normalizedSource, Set<String> sourceNumbers,
                             List<String> warnings, String path) {
        if (text.isBlank() || !NARRATIVE_FIELDS.contains(path)) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        for (String sentence : SENTENCE_SPLIT.split(text)) {
            if (sentence.isBlank()) {
                continue;
            }
            Matcher matcher = NUMBER.matcher(sentence);
            boolean fabricated = false;
            while (matcher.find()) {
                if (!sourceNumbers.contains(matcher.group())) {
                    fabricated = true;
                    break;
                }
            }
            if (fabricated) {
                warnings.add("已删除包含未提供数字的表述：" + sentence.trim());
            } else {
                builder.append(sentence);
            }
        }
        return builder.toString().trim();
    }

    private static JsonNode findByKey(JsonNode beforeArray, String itemKey, int index) {
        if (itemKey != null) {
            for (JsonNode item : beforeArray) {
                if (itemKey.equals(item.path("id").asText(null))) {
                    return item;
                }
            }
        }
        return index < beforeArray.size() ? beforeArray.get(index) : null;
    }

    private static boolean grounded(String normalizedSource, String value) {
        String normalizedValue = normalize(value);
        if (normalizedValue.isEmpty()) {
            return true;
        }
        return normalizedSource.contains(normalizedValue);
    }

    private static Set<String> numbersOf(String text) {
        Set<String> numbers = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return numbers;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：！？、（）【】《》“”‘’]", "");
    }
}
