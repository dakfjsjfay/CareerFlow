package com.team.resume.profile.vo;

import java.util.List;

/**
 * 档案完整度（docs/api.md §6、docs/database.md §6）：
 * 6 个维度 + 平均总分。
 */
public record CompletenessVO(int overall, List<DimensionScore> dimensions) {

    public record DimensionScore(String name, int score) {
    }
}
