package com.team.resume.matching.vo;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;

/**
 * 岗位推荐项（GET /api/match/jobs/recommend）。
 * matchScore 是规则匹配分；rankReasons 说明排序调整原因（可解释性要求）。
 * adjustedScore 仅用于排序，不返回给前端。
 */
@Data
public class RecommendItemVO {

    private Long jobId;

    private String company;

    private String position;

    private String city;

    private String salary;

    private BigDecimal matchScore;

    private List<String> rankReasons;

    /** 排序内部使用，不序列化给前端 */
    @JsonIgnore
    private BigDecimal adjustedScore;
}
