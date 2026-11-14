package com.team.resume.resume.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.team.resume.resume.edit.service.CandidateFactGuard;

/**
 * 事实校验回归数据集：覆盖「保留真实数字 / 删除编造数字 / 回退未提供事实」等场景。
 * 数据集见 src/test/resources/star-test-dataset.json，后续新增规则时持续补充。
 */
class CandidateFactGuardDatasetTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private CandidateFactGuard guard;
    private JsonNode dataset;

    @BeforeEach
    void setUp() throws Exception {
        guard = new CandidateFactGuard(objectMapper);
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("star-test-dataset.json")) {
            dataset = objectMapper.readTree(input);
        }
    }

    @Test
    void datasetCoversAllGuarantees() {
        assertTrue(dataset.isArray() && dataset.size() >= 5);
        for (JsonNode testCase : dataset) {
            String name = testCase.path("name").asText();
            JsonNode source = testCase.get("source");
            String sourceText = source.isTextual() ? source.asText() : source.toString();
            sourceText = sourceText + " " + testCase.path("userText").asText();

            CandidateFactGuard.SanitizeResult result = guard.sanitize(
                    testCase.get("after"), source, sourceText);
            JsonNode expect = testCase.get("expect");

            if (expect.has("hasWarnings")) {
                assertEquals(expect.path("hasWarnings").asBoolean(),
                        !result.warnings().isEmpty(), name + "：告警数量不符合预期");
            }
            if (expect.has("resultContains")) {
                assertTrue(result.content().path("star").path("result").asText()
                        .contains(expect.path("resultContains").asText()), name);
            }
            if (expect.has("resultNotContains")) {
                assertFalse(result.content().path("star").path("result").asText()
                        .contains(expect.path("resultNotContains").asText()), name);
            }
            if (expect.has("companyEquals")) {
                assertEquals(expect.path("companyEquals").asText(),
                        result.content().path("company").asText(), name);
            }
            if (expect.path("descriptionAbsent").asBoolean(false)) {
                assertFalse(result.content().has("description"), name + "：AI 新增字段必须移除");
            }
            if (expect.has("skillsEqual")) {
                List<String> skills = objectMapper.convertValue(result.content().path("skills"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                assertEquals(expect.path("skillsEqual").size(), skills.size(), name);
                assertEquals(expect.path("skillsEqual").get(0).asText(), skills.get(0), name);
            }
            if (expect.has("descriptionContains")) {
                assertTrue(result.content().path("description").asText()
                        .contains(expect.path("descriptionContains").asText()), name);
            }
        }
    }
}
