package com.team.resume.resume.draft.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 经历智能追问：完全确定性规则，不调用 AI，避免把缺失信息“编”出来。
 * <p>
 * 只针对缺失或过短的 STAR 要素提问，已填写充分的不再打扰用户。
 */
@Component
public class StarFollowUpAdvisor {

    private static final int MIN_LENGTH = 10;

    private final ObjectMapper objectMapper;

    public StarFollowUpAdvisor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode advise(String experienceId, JsonNode item) {
        JsonNode star = item.path("star");
        JsonNode description = item.path("description");
        List<ObjectNode> questions = new ArrayList<>();
        int filled = 0;

        ObjectNode situation = question("SITUATION", "这段经历发生在什么业务场景或团队中？当时的背景是什么？");
        if (needsMore(star.path("situation"), description)) {
            questions.add(situation);
        } else {
            filled++;
        }

        ObjectNode task = question("TASK", "你当时具体负责的任务或目标是什么？需要解决的核心问题是什么？");
        if (needsMore(star.path("task"), null)) {
            questions.add(task);
        } else {
            filled++;
        }

        ObjectNode action = question("ACTION", "你采取了哪些具体行动？用了什么技术、方法或协作方式？");
        if (needsMore(star.path("action"), null)) {
            questions.add(action);
        } else {
            filled++;
        }

        ObjectNode result = question("RESULT", "最终结果如何？有可量化的指标吗（如耗时、QPS、用户量、提升比例）？");
        if (needsMore(star.path("result"), item.path("achievements"))) {
            questions.add(result);
        } else {
            filled++;
        }

        ObjectNode vo = objectMapper.createObjectNode();
        vo.put("experienceId", experienceId);
        vo.put("completeness", filled * 25);
        vo.set("questions", objectMapper.valueToTree(questions));
        return vo;
    }

    private static boolean needsMore(JsonNode field, JsonNode fallback) {
        if (field != null && field.isTextual() && field.asText().trim().length() >= MIN_LENGTH) {
            return false;
        }
        return fallback == null || !fallback.isTextual() || fallback.asText().trim().length() < MIN_LENGTH;
    }

    private ObjectNode question(String key, String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", key);
        node.put("question", text);
        return node;
    }
}
