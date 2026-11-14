package com.team.resume.optimization.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 反向优化任务（optimization_task）：针对某个岗位的某项技能差距发起多轮追问。
 * 状态机见 docs/api.md §10：
 * WAITING_USER → ANSWER_RECEIVED → LLM_EXTRACTION → USER_CONFIRMATION → PROFILE_UPDATED → RESUME_REGENERATED
 * 中间态（ANSWER_RECEIVED / LLM_EXTRACTION / PROFILE_UPDATED）在事务内短暂存在，
 * 落库的稳定状态为 WAITING_USER / USER_CONFIRMATION / RESUME_REGENERATED。
 */
@Data
@TableName("optimization_task")
public class OptimizationTask {

    public static final String STATUS_WAITING_USER = "WAITING_USER";
    public static final String STATUS_ANSWER_RECEIVED = "ANSWER_RECEIVED";
    public static final String STATUS_LLM_EXTRACTION = "LLM_EXTRACTION";
    public static final String STATUS_USER_CONFIRMATION = "USER_CONFIRMATION";
    public static final String STATUS_PROFILE_UPDATED = "PROFILE_UPDATED";
    public static final String STATUS_RESUME_REGENERATED = "RESUME_REGENERATED";

    public static final String SOURCE_JD_GAP = "JD_GAP_ANALYSIS";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 目标岗位 */
    private Long jobId;

    /** 发起任务时的简历版本 */
    private Long resumeVersionId;

    /** 追问复用的对话会话 */
    private Long chatSessionId;

    /** 待确认技能（如 Spring Boot） */
    private String targetSkill;

    /** 当前追问内容 */
    private String question;

    /** 任务状态（状态机见类注释） */
    private String status;

    /** 来源：JD_GAP_ANALYSIS */
    private String source;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
