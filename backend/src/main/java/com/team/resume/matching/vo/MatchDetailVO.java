package com.team.resume.matching.vo;

import lombok.Data;

/**
 * 逐技能匹配详情（docs/api.md §9）：状态 + 证据 + 建议——分数可解释的载体。
 */
@Data
public class MatchDetailVO {

    private String skill;

    /** MATCHED / PARTIAL / MISSING / UNCONFIRMED */
    private String status;

    private String evidence;

    private String suggestion;
}
