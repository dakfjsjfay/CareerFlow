package com.team.resume.ai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.DocumentAnalysisResult;
import com.team.resume.ai.dto.JobRequirementsResult;
import com.team.resume.ai.dto.Message;
import com.team.resume.ai.dto.ResumeGenerationResult;

/**
 * 业务侧统一 AI 入口（架构文档 §4）。
 * 业务模块不直接依赖 DeepSeekClient，将来换模型只改这里和 client。
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    /**
     * 实测部分长上下文会让模型返回纯空白 content（finish_reason=stop）。
     * 重试时在末尾追加一条输出约束，可稳定打破该状态。
     */
    private static final String RETRY_NUDGE =
            "输出要求：必须只输出一个合法 JSON 对象，reply 字段不能为空；如果信息不足，就在 reply 中继续追问。";

    private final DeepSeekClient deepSeekClient;
    private final PromptTemplate promptTemplate;
    private final AIResponseParser responseParser;

    public AIService(DeepSeekClient deepSeekClient,
                     PromptTemplate promptTemplate,
                     AIResponseParser responseParser) {
        this.deepSeekClient = deepSeekClient;
        this.promptTemplate = promptTemplate;
        this.responseParser = responseParser;
    }

    /**
     * 多轮建档对话：带历史上下文调用模型，并解析为结构化结果。
     *
     * @param history     历史消息（role 为 user / assistant，按时间正序）
     * @param userMessage 本轮用户输入
     */
    public ChatExtractionResult chat(List<Message> history, String userMessage) {
        try {
            return chatOnce(history, userMessage, false);
        } catch (EmptyAiContentException e) {
            log.warn("AI 返回空白内容，追加输出约束后重试一次");
            return chatOnce(history, userMessage, true);
        }
    }

    private ChatExtractionResult chatOnce(List<Message> history, String userMessage,
                                          boolean withNudge) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptTemplate.chatExtractPrompt()));
        messages.addAll(history);
        messages.add(new Message("user", userMessage));
        if (withNudge) {
            messages.add(new Message("user", RETRY_NUDGE));
        }

        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseChatResult(content);
    }

    /**
     * 分析上传文件：抽取个人资料/项目信息，生成简历文案，可选按 JD 给出差距建议。
     * 文档内容用 &lt;document&gt; 包裹，并在提示词中声明其只是数据而非指令。
     */
    public DocumentAnalysisResult analyzeDocument(String fileName, String analysisType,
                                                  String jobDescription, String documentText) {
        List<Message> messages = List.of(
                new Message("system", promptTemplate.documentAnalyzePrompt(analysisType, jobDescription)),
                new Message("user", "文件名：" + fileName + "\n\n<document>\n" + documentText + "\n</document>"));

        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseDocumentAnalysis(content);
    }

    /**
     * 简历文案改写：输入是 {profile, baseContent} 的 JSON 字符串，
     * 输出只包含 selfEvaluation 与项目/经历亮点，事实字段不经过模型。
     */
    public ResumeGenerationResult generateResume(String sourceJson) {
        List<Message> messages = List.of(
                new Message("system", promptTemplate.resumeGeneratePrompt()),
                new Message("user", sourceJson));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseResumeGeneration(content);
    }

    /**
     * JD 技能要求解析：输入 JD 原文，输出岗位名称与技能要求。
     * JD 用 &lt;jd&gt; 包裹，提示词中声明其只是数据而非指令。
     */
    public JobRequirementsResult parseJobDescription(String jdContent) {
        List<Message> messages = List.of(
                new Message("system", promptTemplate.jobParsePrompt()),
                new Message("user", "岗位 JD：\n<jd>\n" + jdContent + "\n</jd>"));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseJobRequirements(content);
    }
}
