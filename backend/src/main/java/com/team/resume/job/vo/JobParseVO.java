package com.team.resume.job.vo;

import java.util.List;

import lombok.Data;

/**
 * JD 解析结果（已落库）：position + 技能要求（docs/api.md §8）。
 */
@Data
public class JobParseVO {

    private Long jobId;

    private String position;

    private List<JobRequirementVO> requirements;
}
