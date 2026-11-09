package com.team.resume.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.DocumentAnalysisResult;
import com.team.resume.ai.dto.Message;

/**
 * 业务侧统一 AI 入口（架构文档 §4）。
 * 业务模块不直接依赖 DeepSeekClient，将来换模型只改这里和 client。
 */
@Service
public class AIService {

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
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptTemplate.chatExtractPrompt()));
        messages.addAll(history);
        messages.add(new Message("user", userMessage));

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
}
