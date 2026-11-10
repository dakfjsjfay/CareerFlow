package com.team.resume.resume.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPBdr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.springframework.stereotype.Component;

import com.team.resume.common.BusinessException;
import com.team.resume.resume.vo.ResumeContent;

/**
 * 简历 DOCX 导出（docs/superpowers/specs/2026-11-10-d6-resume-generation-export-design.md §8.4）。
 * <p>
 * DOCX 是干净的单栏专业排版，不追求与 20 套模板 1:1 一致：
 * 姓名 → 联系方式 → 求职意向 → 教育背景 → 技能特长 → 项目经历 → 工作/实习经历 → 自我评价。
 * 空章节直接跳过；用户文本先去掉 HTML 标签，避免把标签写进 Word。
 */
@Component
public class ResumeDocxExporter {

    private static final String FONT = "微软雅黑";
    private static final String ACCENT_COLOR = "2563EB";
    private static final BigInteger PAGE_WIDTH = BigInteger.valueOf(11906);
    private static final BigInteger PAGE_HEIGHT = BigInteger.valueOf(16838);
    private static final BigInteger PAGE_MARGIN = BigInteger.valueOf(1134);

    public byte[] export(ResumeContent content, String resumeName, int versionNumber) {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            setupPage(doc);
            writeHeader(doc, content, resumeName);
            writeJobIntention(doc, content.jobIntention());
            writeEducation(doc, content.education());
            writeSkills(doc, content.skills());
            writeProjects(doc, content.projects());
            writeExperiences(doc, content.experiences());
            writeSelfEvaluation(doc, content.selfEvaluation());
            doc.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "简历导出失败");
        }
    }

    private static void setupPage(XWPFDocument doc) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        pageSz.setW(PAGE_WIDTH);
        pageSz.setH(PAGE_HEIGHT);
        CTPageMar margin = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        margin.setTop(PAGE_MARGIN);
        margin.setBottom(PAGE_MARGIN);
        margin.setLeft(PAGE_MARGIN);
        margin.setRight(PAGE_MARGIN);
    }

    private static void writeHeader(XWPFDocument doc, ResumeContent content, String resumeName) {
        ResumeContent.PersonalInfo info = content.personalInfo();
        String name = info == null || isBlank(info.realName()) ? resumeName : info.realName().trim();
        writeParagraph(doc, name, 18, true, "111827", ParagraphAlignment.CENTER, 120, false);

        List<String> contacts = new ArrayList<>();
        if (info != null) {
            addIfPresent(contacts, info.phone());
            addIfPresent(contacts, info.email());
            addIfPresent(contacts, info.city());
        }
        if (!contacts.isEmpty()) {
            writeParagraph(doc, String.join(" | ", contacts), 10, false, "4B5563",
                    ParagraphAlignment.CENTER, 240, false);
        }
    }

    private static void writeJobIntention(XWPFDocument doc, ResumeContent.JobIntention intention) {
        if (intention == null) {
            return;
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, intention.targetPosition());
        addIfPresent(parts, intention.targetCity());
        addIfPresent(parts, intention.expectedSalary());
        if (parts.isEmpty()) {
            return;
        }
        writeSectionTitle(doc, "求职意向");
        writeParagraph(doc, String.join(" | ", parts), 11, false, "111827",
                ParagraphAlignment.LEFT, 80, false);
    }

    private static void writeEducation(XWPFDocument doc, List<ResumeContent.Education> education) {
        if (education == null || education.isEmpty()) {
            return;
        }
        boolean hasContent = education.stream().anyMatch(item -> item != null
                && (notBlank(item.school()) || notBlank(item.major()) || notBlank(item.degree())));
        if (!hasContent) {
            return;
        }
        writeSectionTitle(doc, "教育背景");
        for (ResumeContent.Education item : education) {
            if (item == null) {
                continue;
            }
            List<String> parts = new ArrayList<>();
            addIfPresent(parts, item.school());
            addIfPresent(parts, item.major());
            addIfPresent(parts, item.degree());
            if (item.graduationYear() != null) {
                parts.add(item.graduationYear() + " 年毕业");
            }
            String range = formatRange(item.startDate(), item.endDate());
            if (!range.isEmpty()) {
                parts.add(range);
            }
            if (!parts.isEmpty()) {
                writeParagraph(doc, String.join(" | ", parts), 11, true, "111827",
                        ParagraphAlignment.LEFT, 60, false);
            }
        }
    }

    private static void writeSkills(XWPFDocument doc, List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return;
        }
        List<String> cleaned = skills.stream()
                .filter(ResumeDocxExporter::notBlank)
                .map(String::trim)
                .toList();
        if (cleaned.isEmpty()) {
            return;
        }
        writeSectionTitle(doc, "技能特长");
        writeParagraph(doc, String.join("、", cleaned), 11, false, "111827",
                ParagraphAlignment.LEFT, 80, false);
    }

    private static void writeProjects(XWPFDocument doc, List<ResumeContent.Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        writeSectionTitle(doc, "项目经历");
        for (ResumeContent.Project project : projects) {
            if (project == null) {
                continue;
            }
            List<String> title = new ArrayList<>();
            addIfPresent(title, project.projectName());
            addIfPresent(title, project.role());
            String range = formatRange(project.startDate(), project.endDate());
            if (!range.isEmpty()) {
                title.add(range);
            }
            if (!title.isEmpty()) {
                writeParagraph(doc, String.join(" | ", title), 11, true, "111827",
                        ParagraphAlignment.LEFT, 40, false);
            }
            for (String line : plainLines(project.description())) {
                writeBullet(doc, line);
            }
            if (project.techStack() != null && !project.techStack().isEmpty()) {
                writeBullet(doc, "技术栈：" + String.join("、", project.techStack()));
            }
            if (project.highlights() != null) {
                project.highlights().forEach(highlight -> writeBullet(doc, highlight));
            }
        }
    }

    private static void writeExperiences(XWPFDocument doc,
                                         List<ResumeContent.Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }
        writeSectionTitle(doc, "工作/实习经历");
        for (ResumeContent.Experience experience : experiences) {
            if (experience == null) {
                continue;
            }
            List<String> title = new ArrayList<>();
            addIfPresent(title, experience.company());
            addIfPresent(title, experience.position());
            String range = formatRange(experience.startDate(), experience.endDate());
            if (!range.isEmpty()) {
                title.add(range);
            }
            if (!title.isEmpty()) {
                writeParagraph(doc, String.join(" | ", title), 11, true, "111827",
                        ParagraphAlignment.LEFT, 40, false);
            }
            for (String line : plainLines(experience.description())) {
                writeBullet(doc, line);
            }
            for (String line : plainLines(experience.achievements())) {
                writeBullet(doc, line);
            }
            if (experience.highlights() != null) {
                experience.highlights().forEach(highlight -> writeBullet(doc, highlight));
            }
        }
    }

    private static void writeSelfEvaluation(XWPFDocument doc, String selfEvaluation) {
        List<String> lines = plainLines(selfEvaluation);
        if (lines.isEmpty()) {
            return;
        }
        writeSectionTitle(doc, "自我评价");
        for (String line : lines) {
            writeParagraph(doc, line, 11, false, "111827",
                    ParagraphAlignment.LEFT, 80, false);
        }
    }

    private static void writeSectionTitle(XWPFDocument doc, String title) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setSpacingBefore(240);
        paragraph.setSpacingAfter(100);
        XWPFRun run = paragraph.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(14);
        applyFont(run);
        run.setColor(ACCENT_COLOR);

        CTPPr pPr = paragraph.getCTP().isSetPPr()
                ? paragraph.getCTP().getPPr()
                : paragraph.getCTP().addNewPPr();
        CTPBdr border = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
        CTBorder bottom = border.isSetBottom() ? border.getBottom() : border.addNewBottom();
        bottom.setVal(STBorder.SINGLE);
        bottom.setSz(BigInteger.valueOf(6));
        bottom.setSpace(BigInteger.valueOf(1));
        bottom.setColor(ACCENT_COLOR);
    }

    private static void writeBullet(XWPFDocument doc, String text) {
        writeParagraph(doc, "• " + text, 10, false, "374151",
                ParagraphAlignment.LEFT, 40, true);
    }

    private static void writeParagraph(XWPFDocument doc, String text, int fontSize, boolean bold,
                                       String color, ParagraphAlignment alignment,
                                       int spacingAfter, boolean indent) {
        XWPFParagraph paragraph = doc.createParagraph();
        paragraph.setAlignment(alignment);
        paragraph.setSpacingAfter(spacingAfter);
        if (indent) {
            paragraph.setIndentationLeft(240);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontSize(fontSize);
        run.setColor(color);
        applyFont(run);
    }

    private static void applyFont(XWPFRun run) {
        run.setFontFamily(FONT);
        run.setFontFamily(FONT, XWPFRun.FontCharRange.eastAsia);
    }

    /**
     * 去掉富文本标签并按行拆分；Word 里只需要纯文本。
     */
    private static List<String> plainLines(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    private static String formatRange(LocalDate start, LocalDate end) {
        String startText = formatMonth(start);
        String endText = formatMonth(end);
        if (startText.isEmpty() && endText.isEmpty()) {
            return "";
        }
        return (startText.isEmpty() ? "未知" : startText)
                + " - "
                + (endText.isEmpty() ? "至今" : endText);
    }

    private static String formatMonth(LocalDate date) {
        return date == null ? "" : date.getYear() + "." + String.format("%02d", date.getMonthValue());
    }

    private static void addIfPresent(List<String> values, String value) {
        if (notBlank(value)) {
            values.add(value.trim());
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
