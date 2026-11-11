package com.team.resume.matching.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 匹配详情表（match_detail）：每个岗位技能一行，包含状态、证据与建议——
 * 匹配分数可解释性的载体（赛题核心要求）。
 */
@Data
@TableName("match_detail")
public class MatchDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long matchResultId;

    private String skill;

    /** MATCHED / PARTIAL / MISSING / UNCONFIRMED */
    private String matchStatus;

    private String evidence;

    private String suggestion;
}
