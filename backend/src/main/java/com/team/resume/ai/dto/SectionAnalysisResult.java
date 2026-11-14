package com.team.resume.ai.dto;

import java.util.List;

/**
 * 单区块 AI 分析结果（不落库）。
 */
public record SectionAnalysisResult(String sectionKey,
                                    int score,
                                    List<String> issues,
                                    List<String> suggestions,
                                    List<String> missingFields,
                                    boolean factsConfirmed) {
}
