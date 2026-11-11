package com.team.resume.job.vo;

import java.math.BigDecimal;

public record JobRequirementVO(Long id, String skill, String importance, BigDecimal weight) {
}
