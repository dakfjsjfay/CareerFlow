package com.team.resume.matching.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 投递建议结果（docs/api.md §9.2）：话术只使用已确认且有证据的信息。
 */
@Data
public class AdviceVO {

    private BigDecimal matchScore;

    private List<String> strengths;

    private List<String> gaps;

    private String pitchMessage;

    private List<String> suggestions;
}
