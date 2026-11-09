package com.team.resume.profile.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 宽松读取 AI 抽取 JSON 的小工具。
 * 模型偶尔会用 snake_case 或别名（name/project_name），这里统一做兼容，
 * 但绝不凭空造值：字段不存在或为空一律返回 null / 空列表。
 */
public final class JsonValues {

    private JsonValues() {
    }

    public static String text(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isValueNode() && !value.isNull() && !value.isBoolean()) {
                String text = value.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }

    public static JsonNode object(JsonNode parent, String... keys) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = parent.get(key);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return null;
    }

    public static JsonNode array(JsonNode parent, String... keys) {
        if (parent == null || !parent.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = parent.get(key);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return null;
    }

    public static List<String> stringList(JsonNode node, String... keys) {
        JsonNode value = array(node, keys);
        if (value == null) {
            String single = text(node, keys);
            return single == null ? List.of() : List.of(single);
        }
        Set<String> unique = new LinkedHashSet<>();
        value.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                unique.add(item.asText().trim());
            }
        });
        return new ArrayList<>(unique);
    }
}
