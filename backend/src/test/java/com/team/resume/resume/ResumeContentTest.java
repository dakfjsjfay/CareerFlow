package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.resume.resume.vo.ResumeContent;

/**
 * 简历内容模型（docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md §6）：
 * 必须能稳定地在 Java record 与 JSON 之间往返，尤其是 templateId 与 AI 亮点。
 */
class ResumeContentTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsMetaAndHighlights() throws Exception {
        ResumeContent content = new ResumeContent(
                new ResumeContent.Meta("tpl-1", "模板一", "2026-11-10T12:00:00", "AI",
                        "INITIAL", null, List.of()),
                new ResumeContent.PersonalInfo("张同学", "13800000000", null, null, null,
                        "深圳大学", "软件工程", "本科", 2028, 0, "简介"),
                new ResumeContent.JobIntention("Java 开发实习生", "深圳", "面议"),
                List.of(new ResumeContent.Education("深圳大学", "软件工程", "本科", 2028,
                        null, null)),
                List.of("Java"),
                List.of(new ResumeContent.Project("学生系统", "后端", "描述", List.of("Java"),
                        List.of("亮点"), null, null)),
                List.of(new ResumeContent.Experience("某公司", "实习生", "INTERNSHIP", "描述",
                        "成果", List.of("亮点"), null, null)),
                "自我评价");

        JsonNode node = mapper.valueToTree(content);
        ResumeContent back = mapper.treeToValue(node, ResumeContent.class);

        assertEquals("tpl-1", back.meta().templateId());
        assertEquals("亮点", back.projects().get(0).highlights().get(0));
        assertEquals("INTERNSHIP", back.experiences().get(0).type());
        assertEquals("Java", back.skills().get(0));
    }
}
