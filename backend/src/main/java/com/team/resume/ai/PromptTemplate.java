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
    private final String jobParsePrompt;

    public PromptTemplate() {
        this.chatExtractPrompt = read("prompts/chat-extract-prompt.txt");
        this.documentAnalyzePrompt = read("prompts/document-analyze-prompt.txt");
        this.resumeGeneratePrompt = read("prompts/resume-generate-prompt.txt");
        this.jobParsePrompt = read("prompts/job-parse-prompt.txt");
    }

    public String chatExtractPrompt() {
        return chatExtractPrompt;
    }

    public String resumeGeneratePrompt() {
        return resumeGeneratePrompt;
    }

    public String jobParsePrompt() {
        return jobParsePrompt;
    }

    public String documentAnalyzePrompt(String analysisType, String jobDescription) {
        String jd = jobDescription == null || jobDescription.isBlank()
                ? "未提供"
                : jobDescription.trim();
        return documentAnalyzePrompt
                .replace("{{analysisType}}", analysisType)
                .replace("{{jobDescription}}", jd);
    }

    private static String read(String classpath) {
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载提示词失败: " + classpath, e);
        }
    }
}
