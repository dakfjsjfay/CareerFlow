package com.team.resume.resume.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.ResumeGenerationResult;
import com.team.resume.ai.dto.ResumeGenerationResult.Highlight;
import com.team.resume.common.BusinessException;
import com.team.resume.profile.support.FactGuard;
import com.team.resume.profile.vo.ProfileVO;
import com.team.resume.resume.vo.ResumeContent;

/**
 * AI 简历文案增强（docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md §8.3）。
 * <p>
 * 事实部分由 {@link ResumeContentAssembler} 组装完成；这里只接受模型对 selfEvaluation
 * 和项目/经历亮点的改写，并逐句用 {@link FactGuard} 校验，找不到依据的句子直接丢弃。
 * AI 调用本身失败继续向上抛 502；事实校验失败则保留兜底文案并写 warning。
 */
@Service
public class ResumeAiService {

    static final String WARNING_FACT_CHECK_FALLBACK = "AI_FACT_CHECK_FALLBACK";
    private static final int ABSTRACT_MAX_LENGTH = 60;

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    public ResumeAiService(AIService aiService, ObjectMapper objectMapper) {
        this.aiService = aiService;
        this.objectMapper = objectMapper;
    }

    public ResumeContent enhance(ResumeContent base, ProfileVO profile, List<String> warnings) {
        String sourceText = collectText(profile) + " " + collectText(base);
        String sourceJson;
        try {
            sourceJson = objectMapper.writeValueAsString(Map.of(
                    "profile", profile,
                    "baseContent", base));
        } catch (JsonProcessingException e) {
            throw new BusinessException(500, "简历内容序列化失败");
        }

        ResumeGenerationResult suggestion = aiService.generateResume(sourceJson);
        String selfEvaluation = applySelfEvaluation(
                base.selfEvaluation(), suggestion.selfEvaluation(), sourceText, warnings);

        List<ResumeContent.Experience> experiences = applyExperienceHighlights(
                base.experiences(), suggestions(suggestion.experienceHighlights()), sourceText, warnings);
        List<ResumeContent.Project> projects = applyProjectHighlights(
                base.projects(), suggestions(suggestion.projectHighlights()), sourceText, warnings);

        ResumeContent.PersonalInfo info = base.personalInfo();
        ResumeContent.PersonalInfo updatedInfo = new ResumeContent.PersonalInfo(
                info.realName(), info.phone(), info.email(), info.city(), info.avatar(),
                info.school(), info.major(), info.degree(), info.graduationYear(),
                info.yearsOfExperience(), firstSentence(selfEvaluation));

        ResumeContent.Meta meta = base.meta();
        ResumeContent.Meta updatedMeta = new ResumeContent.Meta(
                meta.templateId(), meta.templateName(), meta.generatedAt(), meta.source(),
                meta.generationReason(), meta.targetJobId(), warnings);

        return new ResumeContent(updatedMeta, updatedInfo, base.jobIntention(), base.education(),
                base.skills(), projects, experiences, selfEvaluation);
    }

    private static String applySelfEvaluation(String base, String suggestion, String sourceText,
                                              List<String> warnings) {
        if (suggestion == null || suggestion.isBlank()) {
            addWarning(warnings);
            return base;
        }
        List<String> sentences = splitSentences(suggestion);
        List<String> grounded = sentences.stream()
                .filter(sentence -> FactGuard.isGrounded(sourceText, sentence))
                .toList();
        if (grounded.isEmpty()) {
            addWarning(warnings);
            return base;
        }
        if (grounded.size() < sentences.size()) {
            addWarning(warnings);
        }
        return String.join("", grounded);
    }

    private static List<ResumeContent.Experience> applyExperienceHighlights(
            List<ResumeContent.Experience> base, List<Highlight> suggestions,
            String sourceText, List<String> warnings) {
        if (base == null || base.isEmpty()) {
            return base == null ? List.of() : base;
        }
        List<ResumeContent.Experience> result = new ArrayList<>(base);
        for (Highlight suggestion : suggestions) {
            if (suggestion.index() >= base.size()) {
                addWarning(warnings);
                continue;
            }
            List<String> grounded = groundedHighlights(suggestion.highlights(), sourceText, warnings);
            if (grounded.isEmpty()) {
                continue;
            }
            ResumeContent.Experience old = result.get(suggestion.index());
            result.set(suggestion.index(), new ResumeContent.Experience(
                    old.company(), old.position(), old.type(), old.description(),
                    old.achievements(), grounded, old.startDate(), old.endDate()));
        }
        return result;
    }

    private static List<ResumeContent.Project> applyProjectHighlights(
            List<ResumeContent.Project> base, List<Highlight> suggestions,
            String sourceText, List<String> warnings) {
        if (base == null || base.isEmpty()) {
            return base == null ? List.of() : base;
        }
        List<ResumeContent.Project> result = new ArrayList<>(base);
        for (Highlight suggestion : suggestions) {
            if (suggestion.index() >= base.size()) {
                addWarning(warnings);
                continue;
            }
            List<String> grounded = groundedHighlights(suggestion.highlights(), sourceText, warnings);
            if (grounded.isEmpty()) {
                continue;
            }
            ResumeContent.Project old = result.get(suggestion.index());
            result.set(suggestion.index(), new ResumeContent.Project(
                    old.projectName(), old.role(), old.description(), old.techStack(),
                    grounded, old.startDate(), old.endDate()));
        }
        return result;
    }

    private static List<String> groundedHighlights(List<String> highlights, String sourceText,
                                                   List<String> warnings) {
        if (highlights == null || highlights.isEmpty()) {
            return List.of();
        }
        List<String> grounded = highlights.stream()
                .filter(highlight -> FactGuard.isGrounded(sourceText, highlight))
                .toList();
        if (grounded.size() < highlights.size()) {
            addWarning(warnings);
        }
        return grounded;
    }

    private static List<Highlight> suggestions(List<Highlight> highlights) {
        return highlights == null ? List.of() : highlights;
    }

    private static void addWarning(List<String> warnings) {
        if (warnings != null && !warnings.contains(WARNING_FACT_CHECK_FALLBACK)) {
            warnings.add(WARNING_FACT_CHECK_FALLBACK);
        }
    }

    private static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if ("。！？!?".indexOf(current) >= 0) {
                String sentence = text.substring(start, i + 1).trim();
                if (!sentence.isEmpty()) {
                    sentences.add(sentence);
                }
                start = i + 1;
            }
        }
        String tail = text.substring(start).trim();
        if (!tail.isEmpty()) {
            sentences.add(tail);
        }
        return sentences;
    }

    private static String firstSentence(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim();
        int end = -1;
        for (char mark : new char[] {'。', '！', '？', '!', '?'}) {
            int index = trimmed.indexOf(mark);
            if (index >= 0 && (end < 0 || index < end)) {
                end = index;
            }
        }
        String sentence = end >= 0 ? trimmed.substring(0, end + 1) : trimmed;
        return sentence.length() > ABSTRACT_MAX_LENGTH
                ? sentence.substring(0, ABSTRACT_MAX_LENGTH)
                : sentence;
    }

    private String collectText(Object value) {
        StringBuilder builder = new StringBuilder();
        appendText(objectMapper.valueToTree(value), builder);
        return builder.toString();
    }

    private static void appendText(JsonNode node, StringBuilder builder) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            builder.append(node.asText()).append(' ');
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> appendText(item, builder));
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> appendText(entry.getValue(), builder));
        }
    }
}
