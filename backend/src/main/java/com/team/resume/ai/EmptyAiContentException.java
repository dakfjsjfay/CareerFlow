package com.team.resume.ai;

import com.team.resume.common.BusinessException;

/**
 * 模型 HTTP 200 但 content 为空或纯空白（实测某些长上下文会稳定出现）。
 * 单独类型便于 AIService 追加输出约束后重试一次；最终仍失败按 502 返回。
 */
public class EmptyAiContentException extends BusinessException {

    public EmptyAiContentException() {
        super(502, "AI 返回内容为空，请稍后重试");
    }
}
