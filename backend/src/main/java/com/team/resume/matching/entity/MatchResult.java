package com.team.resume.matching.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 匹配结果表（match_result）：必须关联简历版本（不是 resume 本身），
 * 保证「哪一版简历匹配了这个岗位」可追溯。
 */
@Data
@TableName("match_result")
public class MatchResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeVersionId;

    private Long jobId;

    private BigDecimal matchScore;

    private String analysisJson;

    private LocalDateTime createdAt;
}
