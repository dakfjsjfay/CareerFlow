package com.team.resume.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 提示词加载器：启动时从 classpath 读取并缓存。
 * 提示词放在 resources/prompts/ 下，便于 AI/测试成员独立维护。
 */
@Component
public class PromptTemplate {

    private final String chatExtractPrompt;
    private final String documentAnalyzePrompt;
    private final String resumeGeneratePrompt;
    private final String jdParsePrompt;
    private final String optimizationQuestionPrompt;
    private final String optimizationReplyPrompt;
    private final String optimizationExtractPrompt;
    private final String matchAdvicePrompt;
    private final String sectionAnalyzePrompt;
    private final String sectionRewritePrompt;
    private final String sectionChatPrompt;
    private final String starStructurePrompt;

    public PromptTemplate() {
        this.chatExtractPrompt = read("prompts/chat-extract-prompt.txt");
        this.documentAnalyzePrompt = read("prompts/document-analyze-prompt.txt");
        this.resumeGeneratePrompt = read("prompts/resume-generate-prompt.txt");
        this.jdParsePrompt = read("prompts/jd-parse-prompt.txt");
        this.optimizationQuestionPrompt = read("prompts/optimization-question-prompt.txt");
        this.optimizationReplyPrompt = read("prompts/optimization-reply-prompt.txt");
        this.optimizationExtractPrompt = read("prompts/optimization-extract-prompt.txt");
        this.matchAdvicePrompt = read("prompts/match-advice-prompt.txt");
        this.sectionAnalyzePrompt = read("prompts/resume-section-analyze-prompt.txt");
        this.sectionRewritePrompt = read("prompts/resume-section-rewrite-prompt.txt");
        this.sectionChatPrompt = read("prompts/resume-section-chat-prompt.txt");
        this.starStructurePrompt = read("prompts/resume-star-prompt.txt");
    }

    public String sectionAnalyzePrompt() {
        return sectionAnalyzePrompt;
    }

    public String sectionRewritePrompt() {
        return sectionRewritePrompt;
    }

    public String sectionChatPrompt() {
        return sectionChatPrompt;
    }

    public String starStructurePrompt() {
        return starStructurePrompt;
    }

    public String matchAdvicePrompt() {
        return matchAdvicePrompt;
    }

    public String chatExtractPrompt() {
        return chatExtractPrompt;
    }

    public String resumeGeneratePrompt() {
        return resumeGeneratePrompt;
    }

    public String jdParsePrompt() {
        return jdParsePrompt;
    }

    public String documentAnalyzePrompt(String analysisType, String jobDescription) {
        String jd = jobDescription == null || jobDescription.isBlank()
                ? "未提供"
                : jobDescription.trim();
        return documentAnalyzePrompt
                .replace("{{analysisType}}", analysisType)
                .replace("{{jobDescription}}", jd);
    }

    public String optimizationQuestionPrompt(String targetSkill, String position) {
        return optimizationQuestionPrompt
                .replace("{{targetSkill}}", targetSkill)
                .replace("{{position}}", position == null || position.isBlank() ? "未提供" : position.trim());
    }

    public String optimizationReplyPrompt(String targetSkill) {
        return optimizationReplyPrompt.replace("{{targetSkill}}", targetSkill);
    }

    public String optimizationExtractPrompt(String targetSkill) {
        return optimizationExtractPrompt.replace("{{targetSkill}}", targetSkill);
    }

    private static String read(String classpath) {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载提示词失败: " + classpath, e);
        }
    }
}
