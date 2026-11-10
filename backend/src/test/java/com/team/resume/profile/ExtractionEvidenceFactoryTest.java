package com.team.resume.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.profile.entity.ProfileEvidence;
import com.team.resume.profile.service.EvidenceSource;
import com.team.resume.profile.service.ExtractionEvidenceFactory;

/**
 * 抽取结果 → PENDING 证据的映射测试（包含 Java 侧事实校验与来源归属）。
 */
class ExtractionEvidenceFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExtractionEvidenceFactory factory = new ExtractionEvidenceFactory();

    private static final String FULL_SOURCE = """
            我叫张三，电话 13800000000，就读于某某大学计算机科学与技术专业，本科，2027 年毕业。
            我学过 Java 和 MySQL。我用 Java 和 MySQL 做过学生管理系统，负责后端开发，实现增删改查，
            2025 年 3 月到 6 月。我在某某科技做后端实习生，参与接口开发，2025 年 7 月开始。
            我想找 Java 后端开发的岗位，城市杭州，期望薪资 8-12K。
            """;

    @Test
    void splitsNestedExtractionIntoPerFieldEvidence() throws Exception {
        JsonNode info = objectMapper.readTree("""
                {
                  "basic": { "realName": "张三", "phone": "13800000000" },
                  "education": {
                    "school": "某某大学", "major": "计算机科学与技术",
                    "degree": "本科", "graduationYear": 2027
                  },
                  "skills": ["Java", "MySQL", "Java"],
                  "projects": [{
                    "projectName": "学生管理系统", "role": "后端开发",
                    "description": "实现增删改查", "techStack": ["Java", "MySQL"],
                    "startDate": "2025-03", "endDate": "2025-06"
                  }],
                  "experiences": [{
                    "company": "某某科技", "position": "后端实习生",
                    "description": "参与接口开发", "startDate": "2025-07"
                  }],
                  "jobIntention": {
                    "targetPosition": "Java 后端开发", "targetCity": "杭州",
                    "expectedSalary": "8-12K"
                  }
                }
                """);

        List<ProfileEvidence> evidence = factory.build(
                1L, List.of(new EvidenceSource(55L, FULL_SOURCE)), info);

        assertThat(evidence).hasSize(13);
        assertThat(evidence).allSatisfy(e -> {
            assertThat(e.getUserId()).isEqualTo(1L);
            assertThat(e.getSourceType()).isEqualTo(ProfileEvidence.SOURCE_CHAT);
            assertThat(e.getSourceId()).isEqualTo(55L);
            assertThat(e.getConfirmationStatus()).isEqualTo(ProfileEvidence.STATUS_PENDING);
            assertThat(e.getEntityId()).isNull();
        });

        Map<String, String> profileFields = evidence.stream()
                .filter(e -> ProfileEvidence.ENTITY_PROFILE.equals(e.getEntityType()))
                .collect(Collectors.toMap(ProfileEvidence::getFieldName, ProfileEvidence::getEvidenceContent));
        assertThat(profileFields).containsEntry("real_name", "张三")
                .containsEntry("phone", "13800000000")
                .containsEntry("school", "某某大学")
                .containsEntry("major", "计算机科学与技术")
                .containsEntry("degree", "本科")
                .containsEntry("graduation_year", "2027")
                .containsEntry("target_position", "Java 后端开发")
                .containsEntry("target_city", "杭州")
                .containsEntry("expected_salary", "8-12K");

        List<String> skills = evidence.stream()
                .filter(e -> ProfileEvidence.ENTITY_SKILL.equals(e.getEntityType()))
                .map(ProfileEvidence::getEvidenceContent)
                .toList();
        assertThat(skills).containsExactly("Java", "MySQL");

        ProfileEvidence project = evidence.stream()
                .filter(e -> ProfileEvidence.ENTITY_PROJECT.equals(e.getEntityType()))
                .findFirst().orElseThrow();
        assertThat(project.getFieldName()).isEqualTo("project");
        JsonNode projectData = objectMapper.readTree(project.getEvidenceContent());
        assertThat(projectData.path("projectName").asText()).isEqualTo("学生管理系统");
        assertThat(projectData.path("role").asText()).isEqualTo("后端开发");
        assertThat(projectData.path("techStack").size()).isEqualTo(2);
        assertThat(projectData.path("startDate").asText()).isEqualTo("2025-03");

        ProfileEvidence experience = evidence.stream()
                .filter(e -> ProfileEvidence.ENTITY_EXPERIENCE.equals(e.getEntityType()))
                .findFirst().orElseThrow();
        assertThat(experience.getFieldName()).isEqualTo("experience");
        JsonNode experienceData = objectMapper.readTree(experience.getEvidenceContent());
        assertThat(experienceData.path("company").asText()).isEqualTo("某某科技");
        assertThat(experienceData.path("position").asText()).isEqualTo("后端实习生");
        assertThat(experienceData.has("endDate")).isFalse();
    }

    @Test
    void acceptsFlatFieldsUsedByApiDocExample() throws Exception {
        JsonNode info = objectMapper.readTree("""
                {
                  "major": "计算机相关专业",
                  "grade": "大二",
                  "skills": ["Java"],
                  "projects": [{ "name": "学生管理系统", "techStack": ["Java"] }]
                }
                """);

        List<ProfileEvidence> evidence = factory.build(
                2L,
                List.of(new EvidenceSource(66L, "我是计算机相关专业大二学生，学过 Java，做过学生管理系统。")),
                info);

        assertThat(evidence).extracting(ProfileEvidence::getFieldName)
                .containsExactly("major", "skills", "project");
        // grade 没有对应档案字段，不能编造落库
        assertThat(evidence).noneMatch(e -> "grade".equals(e.getFieldName()));
        assertThat(evidence).filteredOn(e -> "project".equals(e.getFieldName()))
                .singleElement()
                .satisfies(e -> {
                    JsonNode data = objectMapper.readTree(e.getEvidenceContent());
                    assertThat(data.path("projectName").asText()).isEqualTo("学生管理系统");
                });
    }

    @Test
    void attributesEachFactToTheUserMessageWhereItWasStated() throws Exception {
        EvidenceSource first = new EvidenceSource(11L, "我叫张三，就读于某某大学");
        EvidenceSource middle = new EvidenceSource(22L, "我学过 MySQL");
        EvidenceSource current = new EvidenceSource(33L, "我用 Java 做过学生管理系统");

        JsonNode info = objectMapper.readTree("""
                {
                  "basic": { "realName": "张三" },
                  "education": { "school": "某某大学" },
                  "skills": ["MySQL", "Java"],
                  "projects": [{ "projectName": "学生管理系统", "techStack": ["Java"] }]
                }
                """);

        List<ProfileEvidence> evidence = factory.build(1L, List.of(first, middle, current), info);

        assertThat(evidence).hasSize(5);
        assertThat(evidence)
                .extracting(e -> e.getFieldName() + "@" + e.getSourceId())
                .containsExactlyInAnyOrder(
                        "real_name@11", "school@11", "skills@22", "skills@33", "project@33");
    }

    @Test
    void prefersTheMostRecentMessageWhenTheSameFactAppearsAgain() throws Exception {
        List<ProfileEvidence> evidence = factory.build(
                1L,
                List.of(
                        new EvidenceSource(11L, "我叫张三"),
                        new EvidenceSource(22L, "我叫张三")),
                objectMapper.readTree("{\"basic\":{\"realName\":\"张三\"}}"));

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).getSourceId()).isEqualTo(22L);
    }

    @Test
    void discardsHallucinatedExampleWhenSourceHasNoUsableContent() throws Exception {
        // 真实复现：用户消息乱码时，模型把 Prompt 示例当成真实信息输出
        JsonNode hallucinated = objectMapper.readTree("""
                {
                  "basic": { "realName": "张三" },
                  "education": { "school": "某某大学", "major": "计算机科学与技术" },
                  "skills": ["Java", "MySQL"],
                  "projects": [{
                    "projectName": "学生管理系统", "role": "后端开发",
                    "techStack": ["Java", "MySQL"]
                  }],
                  "jobIntention": { "targetPosition": "Java 后端开发" }
                }
                """);

        assertThat(factory.build(1L, List.of(new EvidenceSource(55L, "????")), hallucinated))
                .isEmpty();
    }

    @Test
    void filtersIndividualValuesThatAreNotGroundedInAnyUserMessage() throws Exception {
        JsonNode info = objectMapper.readTree("""
                {
                  "skills": ["Java", "MySQL", "Spring Boot"],
                  "projects": [{
                    "projectName": "学生管理系统",
                    "techStack": ["Java", "MySQL", "Redis"]
                  }]
                }
                """);

        List<ProfileEvidence> evidence = factory.build(
                1L, List.of(new EvidenceSource(55L, "我用 Java 做过学生管理系统")), info);

        assertThat(evidence).filteredOn(e -> ProfileEvidence.ENTITY_SKILL.equals(e.getEntityType()))
                .extracting(ProfileEvidence::getEvidenceContent)
                .containsExactly("Java");
        ProfileEvidence project = evidence.stream()
                .filter(e -> ProfileEvidence.ENTITY_PROJECT.equals(e.getEntityType()))
                .findFirst().orElseThrow();
        JsonNode projectData = objectMapper.readTree(project.getEvidenceContent());
        assertThat(projectData.path("techStack").size()).isEqualTo(1);
        assertThat(projectData.path("techStack").get(0).asText()).isEqualTo("Java");
    }

    @Test
    void ignoresBlankAndIncompleteItems() throws Exception {
        JsonNode info = objectMapper.readTree("""
                {
                  "basic": { "realName": "   " },
                  "skills": ["", "  "],
                  "projects": [{ "description": "没有项目名称" }],
                  "experiences": [{ "position": "没有公司名称" }]
                }
                """);

        assertThat(factory.build(1L, List.of(new EvidenceSource(55L, "任何内容")), info)).isEmpty();
    }

    @Test
    void emptyOrMissingExtractionProducesNoEvidence() throws Exception {
        assertThat(factory.build(1L, List.of(new EvidenceSource(55L, "任何内容")),
                objectMapper.readTree("{}"))).isEmpty();
        assertThat(factory.build(1L, List.of(new EvidenceSource(55L, "任何内容")), null)).isEmpty();
        assertThat(factory.build(null, List.of(new EvidenceSource(55L, "任何内容")),
                objectMapper.readTree("{}"))).isEmpty();
        assertThat(factory.build(1L, List.of(), objectMapper.readTree("{\"skills\":[\"Java\"]}")))
                .isEmpty();
    }

    @Test
    void createsNoExperienceEvidenceWhenUserExplicitlySaysSo() throws Exception {
        List<ProfileEvidence> evidence = factory.build(
                1L,
                List.of(new EvidenceSource(55L, "暑假中期主要负责前端开发，没有实习经历")),
                objectMapper.readTree("{\"experiences\":[]}"));

        assertThat(evidence)
                .filteredOn(e -> "no_experience".equals(e.getFieldName()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getEntityType()).isEqualTo(ProfileEvidence.ENTITY_PROFILE);
                    assertThat(e.getEvidenceContent()).isEqualTo("无工作经历");
                    assertThat(e.getSourceId()).isEqualTo(55L);
                    assertThat(e.getConfirmationStatus())
                            .isEqualTo(ProfileEvidence.STATUS_PENDING);
                });
    }

    @Test
    void doesNotCreateNoExperienceEvidenceWithoutExplicitStatement() throws Exception {
        List<ProfileEvidence> evidence = factory.build(
                1L,
                List.of(new EvidenceSource(55L, "我做过一个学生管理系统")),
                objectMapper.readTree("{\"experiences\":[]}"));

        assertThat(evidence).noneMatch(e -> "no_experience".equals(e.getFieldName()));
    }
}
