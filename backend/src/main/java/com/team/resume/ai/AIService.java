package com.team.resume.ai;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team.resume.ai.dto.ChatExtractionResult;
import com.team.resume.ai.dto.DocumentAnalysisResult;
import com.team.resume.ai.dto.JdParseResult;
import com.team.resume.ai.dto.Message;
import com.team.resume.ai.dto.OptimizationReplyResult;
import com.team.resume.ai.dto.ResumeGenerationResult;
import com.team.resume.ai.dto.SectionAnalysisResult;
import com.team.resume.ai.dto.SectionRewriteResult;
import com.team.resume.ai.dto.StarStructureResult;

/**
 * 业务侧统一 AI 入口（架构文档 §4）。
 * 业务模块不直接依赖 DeepSeekClient，将来换模型只改这里和 client。
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
     * 解析岗位 JD：提取岗位名称与技能要求（必备/加分 + 权重）。
     * 用于岗位中心的「AI 解析 JD」能力。
     */
    public JdParseResult parseJd(String jdContent) {
        List<Message> messages = List.of(
                new Message("system", promptTemplate.jdParsePrompt()),
                new Message("user", "以下是需要解析的 JD 文本：\n\n" + jdContent));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseJdResult(content);
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
     * 反向优化：为某项技能差距生成第一轮自然追问（docs/api.md §10）。
     * 失败时调用方负责降级为确定性追问，这里只向上抛。
     */
    public String generateOptimizationQuestion(String targetSkill, String position) {
        List<Message> messages = List.of(
                new Message("system", promptTemplate.optimizationQuestionPrompt(targetSkill, position)),
                new Message("user", "请针对目标技能「" + targetSkill + "」发起第一轮追问。"));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseOptimizationQuestion(content);
    }

    /**
     * 反向优化：多轮追问。带历史上下文调用模型，抽取本轮新信息并判断是否继续追问。
     */
    public OptimizationReplyResult optimizationReply(List<Message> history, String answer,
                                                     String targetSkill) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptTemplate.optimizationReplyPrompt(targetSkill)));
        messages.addAll(history);
        messages.add(new Message("user", answer));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseOptimizationReply(content);
    }

    /**
     * 投递建议：根据已确认档案事实生成投递话术（docs/api.md §9.2）。
     * 调用方（MatchAdviceService）负责事实校验与确定性兜底。
     */
    public String generateAdvicePitch(String factsJson) {
        List<Message> messages = List.of(
                new Message("system", promptTemplate.matchAdvicePrompt()),
                new Message("user", factsJson));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseAdvicePitch(content);
    }

    /**
     * 反向优化：确认时对整段追问对话做一次性抽取（复用建档对话的抽取结构）。
     */
    public ChatExtractionResult extractOptimizationInfo(List<Message> history, String targetSkill) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptTemplate.optimizationExtractPrompt(targetSkill)));
        messages.addAll(history);
        messages.add(new Message("user", "请从以上对话中抽取与「" + targetSkill + "」相关的新信息。"));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseChatResult(content);
    }

    /**
     * 八步简历：单区块分析（只分析、不落库）。
     */
    public SectionAnalysisResult analyzeSection(String sectionKey, String sectionJson,
                                                String focus, String jobContext) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("sectionKey", sectionKey);
        payload.set("section", readTreeOrText(sectionJson));
        payload.put("focus", focus == null ? "" : focus);
        payload.put("jobContext", jobContext == null ? "" : jobContext);

        List<Message> messages = List.of(
                new Message("system", promptTemplate.sectionAnalyzePrompt()),
                new Message("user", payload.toString()));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseSectionAnalysis(content, sectionKey);
    }

    /**
     * 八步简历：单区块候选改写。调用方必须在写入候选区前做事实校验。
     */
    public SectionRewriteResult rewriteSection(String sectionKey, String sectionJson,
                                               String instruction, String jobContext) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("sectionKey", sectionKey);
        payload.set("section", readTreeOrText(sectionJson));
        payload.put("instruction", instruction == null ? "" : instruction);
        payload.put("jobContext", jobContext == null ? "" : jobContext);

        List<Message> messages = List.of(
                new Message("system", promptTemplate.sectionRewritePrompt()),
                new Message("user", payload.toString()));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseSectionRewrite(content);
    }

    /**
     * 八步简历：区块 AI 对话（无状态，历史由调用方传入）。
     */
    public String chatSection(String sectionKey, String sectionJson, String historyJson, String message) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("sectionKey", sectionKey);
        payload.set("section", readTreeOrText(sectionJson));
        payload.set("history", readTreeOrText(historyJson == null ? "[]" : historyJson));
        payload.put("message", message == null ? "" : message);

        List<Message> messages = List.of(
                new Message("system", promptTemplate.sectionChatPrompt()),
                new Message("user", payload.toString()));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseSectionChat(content);
    }

    /**
     * 八步简历：STAR 结构化润色（不补写用户未填的要素）。
     */
    public StarStructureResult structureStar(String experienceJson, String situation, String task,
                                             String action, String result) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.set("experience", readTreeOrText(experienceJson));
        payload.put("situation", situation == null ? "" : situation);
        payload.put("task", task == null ? "" : task);
        payload.put("action", action == null ? "" : action);
        payload.put("result", result == null ? "" : result);

        List<Message> messages = List.of(
                new Message("system", promptTemplate.starStructurePrompt()),
                new Message("user", payload.toString()));
        String content = deepSeekClient.completeJson(messages);
        return responseParser.parseStarStructure(content);
    }

    private static JsonNode readTreeOrText(String json) {
        if (json == null || json.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return MAPPER.getNodeFactory().textNode(json);
        }
    }
}
