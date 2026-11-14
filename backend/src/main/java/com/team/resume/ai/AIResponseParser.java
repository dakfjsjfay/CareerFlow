package com.team.resume.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.DocumentAnalysisResult;
import com.team.resume.ai.dto.JdParseResult;
import com.team.resume.ai.dto.OptimizationReplyResult;
import com.team.resume.ai.dto.ResumeGenerationResult;
import com.team.resume.ai.dto.ResumeGenerationResult.Highlight;
import com.team.resume.common.BusinessException;

/**
 * AI 输出结构校验（架构文档 §4 要求）：
 * 1) 必须是可解析的 JSON；
 * 2) 必填字段存在且类型正确；
 * 3) 列表字段只保留非空字符串。
 * 校验失败一律返回 502，且不落库。
 */
@Component
public class AIResponseParser {

    private final ObjectMapper objectMapper;

    public AIResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatExtractionResult parseChatResult(String content) {
        JsonNode root = readJson(content);

        String reply = root.path("reply").asText("").trim();
        if (reply.isEmpty()) {
            throw new BusinessException(502, "AI 返回内容不完整，请稍后重试");
        }

        JsonNode extractedInfo = root.get("extractedInfo");
        if (extractedInfo == null || !extractedInfo.isObject()) {
            extractedInfo = objectMapper.createObjectNode();
        }

        return new ChatExtractionResult(reply, extractedInfo,
                readStringList(root, "missingFields"));
    }

    public DocumentAnalysisResult parseDocumentAnalysis(String content) {
        JsonNode root = readJson(content);

        String summary = root.path("summary").asText("").trim();
        if (summary.isEmpty()) {
            summary = "已完成文件分析";
        }

        JsonNode extractedInfo = root.get("extractedInfo");
        if (extractedInfo == null || !extractedInfo.isObject()) {
            extractedInfo = objectMapper.createObjectNode();
        }

        return new DocumentAnalysisResult(
                summary,
                extractedInfo,
                readStringList(root, "strengths"),
                readStringList(root, "resumeSuggestions"),
                readStringList(root, "jobGapSuggestions"),
                readStringList(root, "missingFields"),
                readStringList(root, "warnings"));
    }

    /**
     * JD 解析结果：技能条目缺技能名直接忽略；importance 非法时归为 REQUIRED；
     * weight 超界时收敛到 (0,100]，保留 1 位小数。
     * 一条技能都解析不出来时返回 502（不落库）。
     */
    public JdParseResult parseJdResult(String content) {
        JsonNode root = readJson(content);
        String position = root.path("position").asText("").trim();

        List<JdParseResult.Requirement> requirements = new ArrayList<>();
        JsonNode reqNode = root.get("requirements");
        if (reqNode != null && reqNode.isArray()) {
            reqNode.forEach(item -> {
                String skill = item.path("skill").asText("").trim();
                if (skill.isEmpty()) {
                    return;
                }
                String importance = item.path("importance").asText("").trim()
                        .toUpperCase(Locale.ROOT);
                if (!"REQUIRED".equals(importance) && !"PREFERRED".equals(importance)) {
                    importance = "REQUIRED";
                }
                double weight = item.path("weight").asDouble(0);
                if (weight <= 0) {
                    weight = 10;
                }
                if (weight > 100) {
                    weight = 100;
                }
                requirements.add(new JdParseResult.Requirement(
                        skill,
                        importance,
                        BigDecimal.valueOf(weight).setScale(1, RoundingMode.HALF_UP)));
            });
        }
        if (requirements.isEmpty()) {
            throw new BusinessException(502, "AI 未能从 JD 中解析出技能要求，请稍后重试");
        }
        return new JdParseResult(position, requirements);
    }

    /**
     * 反向优化追问轮次：reply 必填非空；nextQuestion 缺省为 true（宁可多问一轮，不提前结束）。
     */
    public OptimizationReplyResult parseOptimizationReply(String content) {
        JsonNode root = readJson(content);

        String reply = root.path("reply").asText("").trim();
        if (reply.isEmpty()) {
            throw new BusinessException(502, "AI 返回内容不完整，请稍后重试");
        }

        JsonNode extractedInfo = root.get("extractedInfo");
        if (extractedInfo == null || !extractedInfo.isObject()) {
            extractedInfo = objectMapper.createObjectNode();
        }

        boolean nextQuestion = root.path("nextQuestion").asBoolean(true);
        return new OptimizationReplyResult(reply, extractedInfo, nextQuestion,
                readStringList(root, "missingFields"));
    }

    /**
     * 反向优化第一轮追问：question 必填非空。
     */
    public String parseOptimizationQuestion(String content) {
        JsonNode root = readJson(content);
        String question = root.path("question").asText("").trim();
        if (question.isEmpty()) {
            throw new BusinessException(502, "AI 未能生成追问，请稍后重试");
        }
        return question;
    }

    /**
     * 简历文案建议：selfEvaluation 允许为空；亮点条目必须带合法的非负 index，
     * 否则整条忽略（不因为模型多输出一条脏数据就让整个生成失败）。
     */
    public ResumeGenerationResult parseResumeGeneration(String content) {
        JsonNode root = readJson(content);
        String selfEvaluation = root.path("selfEvaluation").asText("").trim();
        return new ResumeGenerationResult(
                selfEvaluation,
                readHighlights(root, "experienceHighlights"),
                readHighlights(root, "projectHighlights"));
    }

    private static List<Highlight> readHighlights(JsonNode parent, String field) {
        List<Highlight> result = new ArrayList<>();
        JsonNode node = parent.get(field);
        if (node == null || !node.isArray()) {
            return result;
        }
        node.forEach(item -> {
            int index = item.path("index").asInt(-1);
            if (index < 0) {
                return;
            }
            List<String> highlights = readStringList(item, "highlights");
            if (!highlights.isEmpty()) {
                result.add(new Highlight(index, highlights));
            }
        });
        return result;
    }

    /**
     * 投递话术：pitchMessage 必填非空。
     */
    public String parseAdvicePitch(String content) {
        JsonNode root = readJson(content);
        String pitch = root.path("pitchMessage").asText("").trim();
        if (pitch.isEmpty()) {
            throw new BusinessException(502, "AI 未能生成投递话术，请稍后重试");
        }
        return pitch;
    }

    private JsonNode readJson(String content) {
        try {
            return objectMapper.readTree(stripCodeFence(content));
        } catch (JsonProcessingException e) {
            throw new BusinessException(502, "AI 返回格式错误，请稍后重试");
        }
    }

    private static List<String> readStringList(JsonNode parent, String field) {
        List<String> result = new ArrayList<>();
        JsonNode node = parent.get(field);
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) {
                    result.add(item.asText().trim());
                }
            });
        }
        return result;
    }

    private static String stripCodeFence(String content) {
        String text = content == null ? "" : content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            if (firstLineEnd >= 0) {
                text = text.substring(firstLineEnd + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }
}
