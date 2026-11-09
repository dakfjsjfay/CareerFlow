package com.team.resume.profile.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 经历证据（profile_evidence）：证据链核心。
 * <p>
 * 约束：
 * - 未确认的抽取数据只存在于本表（PENDING），绝不写入档案实体表；
 * - 只有 CONFIRMED 的证据才允许被简历生成使用；
 * - entity_type/entity_id 用于定位证据对应的档案实体（PROFILE/SKILL 指向 user_profile，
 *   PROJECT/EXPERIENCE 指向记录实体，未创建前为 NULL，确认时回填）。
 */
@Data
@TableName("profile_evidence")
public class ProfileEvidence {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    public static final String ENTITY_PROFILE = "PROFILE";
    public static final String ENTITY_SKILL = "SKILL";
    public static final String ENTITY_PROJECT = "PROJECT";
    public static final String ENTITY_EXPERIENCE = "EXPERIENCE";

    public static final String SOURCE_CHAT = "CHAT";
    public static final String SOURCE_MANUAL = "MANUAL";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String entityType;

    private Long entityId;

    private String fieldName;

    private String sourceType;

    private Long sourceId;

    /** 抽取候选内容；CHAT 来源可通过 source_id 追溯 chat_message 中的用户原话。 */
    private String evidenceContent;

    private String confirmationStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
