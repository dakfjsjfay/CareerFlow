package com.team.resume.resume;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import com.team.resume.resume.service.ResumeDocxExporter;
import com.team.resume.resume.vo.ResumeContent;

class ResumeDocxExporterTest {

    private final ResumeDocxExporter exporter = new ResumeDocxExporter();

    @Test
    void exportsReopenableDocx() throws Exception {
        byte[] bytes = exporter.export(sampleContent("完成接口开发"), "我的简历", 3);

        assertTrue(bytes.length > 1000);
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);

        String text = readText(bytes);
        assertTrue(text.contains("张同学"));
        assertTrue(text.contains("项目经历"));
        assertTrue(text.contains("工作/实习经历"));
        assertTrue(text.contains("学生信息管理系统"));
    }

    @Test
    void skipsEmptySections() throws Exception {
        ResumeContent content = new ResumeContent(
                meta(), personalInfo(), jobIntention(), List.of(), List.of(),
                List.of(), List.of(), "");

        byte[] bytes = exporter.export(content, "我的简历", 1);

        String text = readText(bytes);
        assertFalse(text.contains("项目经历"));
        assertFalse(text.contains("工作/实习经历"));
        assertFalse(text.contains("技能特长"));
        assertFalse(text.contains("自我评价"));
    }

    @Test
    void stripsHtmlTagsFromDescriptions() throws Exception {
        byte[] bytes = exporter.export(sampleContent("<script>alert(1)</script>"), "我的简历", 1);

        String text = readText(bytes);
        assertFalse(text.contains("<script>"));
        assertTrue(text.contains("alert(1)"));
    }

    @Test
    void handlesNullListsAndDates() throws Exception {
        ResumeContent content = new ResumeContent(
                meta(), personalInfo(), null, null, null, null, null, null);

        byte[] bytes = exporter.export(content, "我的简历", 1);

        assertTrue(bytes.length > 1000);
        assertTrue(readText(bytes).contains("张同学"));
    }

    private static String readText(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            return doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static ResumeContent sampleContent(String projectDescription) {
        return new ResumeContent(
                meta(),
                personalInfo(),
                jobIntention(),
                List.of(new ResumeContent.Education("深圳大学", "软件工程", "本科", 2028,
                        LocalDate.of(2024, 9, 1), LocalDate.of(2028, 6, 30))),
                List.of("Java", "MySQL"),
                List.of(new ResumeContent.Project("学生信息管理系统", "后端开发", projectDescription,
                        List.of("Java", "MySQL"), List.of("实现增删改查"),
                        LocalDate.of(2025, 3, 1), LocalDate.of(2025, 6, 30))),
                List.of(new ResumeContent.Experience("某科技有限公司", "后端实习生", "INTERNSHIP",
                        "参与接口开发", "完成 3 个接口", List.of("完成接口联调"),
                        LocalDate.of(2025, 7, 1), LocalDate.of(2025, 9, 30))),
                "熟悉 Java 与 MySQL。");
    }

    private static ResumeContent.Meta meta() {
        return new ResumeContent.Meta("tpl-1", "模板一", "2026-11-10T12:00:00", "AI",
                "INITIAL", null, List.of());
    }

    private static ResumeContent.PersonalInfo personalInfo() {
        return new ResumeContent.PersonalInfo("张同学", "13800000000", null, "深圳", null,
                "深圳大学", "软件工程", "本科", 2028, 0, null);
    }

    private static ResumeContent.JobIntention jobIntention() {
        return new ResumeContent.JobIntention("Java 开发实习生", "深圳", "面议");
    }
}
