package com.team.resume.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.ai.dto.JobRequirementsResult;
import com.team.resume.common.BusinessException;

class JobRequirementsParserTest {

    private final AIResponseParser parser = new AIResponseParser(new ObjectMapper());

    @Test
    void parsesPositionAndRequirements() {
        JobRequirementsResult result = parser.parseJobRequirements("""
                {"position":"Java 开发实习生",
                 "requirements":[
                   {"skill":"Java","importance":"REQUIRED","weight":60},
                   {"skill":"MySQL","importance":"PREFERRED","weight":40}
                 ]}
                """);

        assertThat(result.position()).isEqualTo("Java 开发实习生");
        assertThat(result.requirements()).hasSize(2);
        assertThat(result.requirements().get(0).skill()).isEqualTo("Java");
        assertThat(result.requirements().get(0).weight()).isEqualByComparingTo("60");
    }

    @Test
    void stripsMarkdownFence() {
        JobRequirementsResult result = parser.parseJobRequirements("""
                ```json
                {"position":"测试","requirements":[{"skill":"SQL","importance":"REQUIRED","weight":100}]}
                ```
                """);

        assertThat(result.requirements()).hasSize(1);
    }

    @Test
    void parsesTextualWeight() {
        JobRequirementsResult result = parser.parseJobRequirements("""
                {"position":"测试","requirements":[
                  {"skill":"Java","importance":"REQUIRED","weight":"60"},
                  {"skill":"MySQL","importance":"REQUIRED","weight":"不是数字"}
                ]}
                """);

        assertThat(result.requirements().get(0).weight()).isEqualByComparingTo("60");
        assertThat(result.requirements().get(1).weight()).isNull();
    }

    @Test
    void dropsEntriesWithoutSkill() {
        JobRequirementsResult result = parser.parseJobRequirements("""
                {"position":"测试","requirements":[
                  {"skill":"  ","importance":"REQUIRED","weight":50},
                  {"skill":"Java","importance":"REQUIRED","weight":50}
                ]}
                """);

        assertThat(result.requirements()).hasSize(1);
        assertThat(result.requirements().get(0).skill()).isEqualTo("Java");
    }

    @Test
    void rejectsEmptyRequirements() {
        assertThatThrownBy(() -> parser.parseJobRequirements(
                "{\"position\":\"测试\",\"requirements\":[]}"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);
    }

    @Test
    void rejectsNonJson() {
        assertThatThrownBy(() -> parser.parseJobRequirements("not json"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(502);
    }
}
