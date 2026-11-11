package com.team.resume.ai;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.DocumentAnalysisResult;
import com.team.resume.ai.dto.JobRequirementsResult;
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

    /**
     * JD 解析：只做结构与类型校验，不补全事实。
     * requirements 为空视为 AI 没有完成解析，返回 502，避免写入空技能要求。
     */
    public JobRequirementsResult parseJobRequirements(String content) {
        JsonNode root = readJson(content);
        String position = root.path("position").asText("").trim();

        List<JobRequirementsResult.Requirement> requirements = new ArrayList<>();
        JsonNode node = root.get("requirements");
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String skill = item.path("skill").asText("").trim();
                if (skill.isEmpty()) {
                    return;
                }
                String importance = item.path("importance").asText("").trim();
                BigDecimal weight = null;
                JsonNode weightNode = item.get("weight");
                if (weightNode != null && weightNode.isNumber()) {
                    weight = weightNode.decimalValue();
                } else if (weightNode != null && weightNode.isTextual()) {
                    try {
                        weight = new BigDecimal(weightNode.asText().trim());
                    } catch (NumberFormatException ignored) {
                        // 非数字权重交给归一化规则按 0 处理
                    }
                }
                requirements.add(new JobRequirementsResult.Requirement(skill, importance, weight));
            });
        }
        if (requirements.isEmpty()) {
            throw new BusinessException(502, "AI 未解析出岗位技能要求，请稍后重试");
        }
        return new JobRequirementsResult(position, requirements);
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
