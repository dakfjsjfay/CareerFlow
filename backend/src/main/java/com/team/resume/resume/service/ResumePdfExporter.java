package com.team.resume.resume.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.team.resume.common.BusinessException;
import com.team.resume.resume.vo.ResumeContent;

/**
 * 后端 PDF 导出：使用 PDFBox 生成 A4 版式，中文字体从系统字体或配置路径加载。
 * <p>
 * 只读取版本内容，不修改任何数据；导出失败会给出明确提示，不返回损坏文件。
 */
@Component
public class ResumePdfExporter {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 48f;
    private static final float BODY_SIZE = 10.5f;
    private static final float LINE_GAP = 5f;

    private static final List<String> DEFAULT_FONT_CANDIDATES = List.of(
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/msyh.ttf",
            "C:/Windows/Fonts/simsun.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/System/Library/Fonts/PingFang.ttc");

    private final String configuredFontPath;

    public ResumePdfExporter(@Value("${app.resume.pdf-font-path:}") String configuredFontPath) {
        this.configuredFontPath = configuredFontPath == null ? "" : configuredFontPath.trim();
    }

    public byte[] export(ResumeContent content, String resumeName, int versionNumber) {
        if (content == null) {
            throw new BusinessException(500, "简历内容为空，无法导出 PDF");
        }
        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);
            PdfWriter writer = new PdfWriter(document, font);
            try {
                render(writer, content);
            } finally {
                writer.close();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "PDF 导出失败：" + e.getMessage());
        }
    }

    private void render(PdfWriter writer, ResumeContent content) throws Exception {
        ResumeContent.PersonalInfo personal = content.personalInfo();
        String name = personal != null && personal.realName() != null ? personal.realName() : "我的简历";
        writer.heading(name, 20f);
        writer.drawAvatar(personal == null ? null : personal.avatar());

        List<String> contact = new ArrayList<>();
        if (personal != null) {
            addIfPresent(contact, personal.phone());
            addIfPresent(contact, personal.email());
            addIfPresent(contact, personal.city());
            if (personal.school() != null) {
                addIfPresent(contact, personal.school());
            }
            if (personal.major() != null && personal.degree() != null) {
                addIfPresent(contact, personal.major() + " / " + personal.degree());
            }
        }
        if (!contact.isEmpty()) {
            writer.paragraph(String.join("  |  ", contact), BODY_SIZE, 4f);
        }

        if (content.jobIntention() != null) {
            ResumeContent.JobIntention intention = content.jobIntention();
            List<String> parts = new ArrayList<>();
            addIfPresent(parts, intention.targetPosition());
            addIfPresent(parts, intention.targetCity());
            addIfPresent(parts, intention.expectedSalary());
            if (!parts.isEmpty()) {
                writer.sectionTitle("求职意向");
                writer.paragraph(String.join("  |  ", parts), BODY_SIZE, 0f);
            }
        }

        if (content.education() != null && !content.education().isEmpty()) {
            writer.sectionTitle("教育背景");
            for (ResumeContent.Education education : content.education()) {
                StringBuilder line = new StringBuilder();
                append(line, education.school());
                append(line, education.major());
                append(line, education.degree());
                String range = dateRange(education.startDate(), education.endDate());
                if (!range.isEmpty()) {
                    append(line, range);
                } else if (education.graduationYear() != null) {
                    append(line, education.graduationYear() + " 年毕业");
                }
                writer.paragraph(line.toString(), BODY_SIZE, 2f);
            }
        }

        if (content.skills() != null && !content.skills().isEmpty()) {
            writer.sectionTitle("专业技能");
            writer.paragraph(String.join("、", content.skills()), BODY_SIZE, 0f);
        }

        if (content.projects() != null && !content.projects().isEmpty()) {
            writer.sectionTitle("项目经历");
            for (ResumeContent.Project project : content.projects()) {
                String header = project.projectName() == null ? "" : project.projectName();
                if (project.role() != null && !project.role().isBlank()) {
                    header = header + " · " + project.role();
                }
                String range = dateRange(project.startDate(), project.endDate());
                if (!range.isEmpty()) {
                    header = header + "  " + range;
                }
                writer.itemTitle(header);
                if (project.description() != null && !project.description().isBlank()) {
                    writer.paragraph(project.description(), BODY_SIZE, 2f);
                }
                if (project.techStack() != null && !project.techStack().isEmpty()) {
                    writer.paragraph("技术栈：" + String.join("、", project.techStack()), BODY_SIZE, 2f);
                }
                if (project.highlights() != null) {
                    for (String highlight : project.highlights()) {
                        if (highlight != null && !highlight.isBlank()) {
                            writer.bullet(highlight);
                        }
                    }
                }
            }
        }

        if (content.experiences() != null && !content.experiences().isEmpty()) {
            writer.sectionTitle("实习与工作经历");
            for (ResumeContent.Experience experience : content.experiences()) {
                String header = joinNonBlank(" · ", experience.company(), experience.position());
                String range = dateRange(experience.startDate(), experience.endDate());
                if (!range.isEmpty()) {
                    header = header + "  " + range;
                }
                writer.itemTitle(header);
                if (experience.description() != null && !experience.description().isBlank()) {
                    writer.paragraph(experience.description(), BODY_SIZE, 2f);
                }
                if (experience.achievements() != null && !experience.achievements().isBlank()) {
                    writer.paragraph("成果：" + experience.achievements(), BODY_SIZE, 2f);
                }
            }
        }

        if (content.selfEvaluation() != null && !content.selfEvaluation().isBlank()) {
            writer.sectionTitle("自我评价");
            writer.paragraph(content.selfEvaluation(), BODY_SIZE, 0f);
        }
    }

    private PDFont loadFont(PDDocument document) {
        List<String> candidates = new ArrayList<>();
        if (!configuredFontPath.isEmpty()) {
            candidates.add(configuredFontPath);
        }
        candidates.addAll(DEFAULT_FONT_CANDIDATES);
        for (String candidate : candidates) {
            File file = new File(candidate);
            if (!file.isFile()) {
                continue;
            }
            try {
                return PDType0Font.load(document, file);
            } catch (Exception ignored) {
                // 继续尝试下一个候选字体
            }
        }
        throw new BusinessException(500, "PDF 导出暂不可用：未找到中文字体，可通过 app.resume.pdf-font-path 指定");
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isBlank()) {
            list.add(value.trim());
        }
    }

    private static void append(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" · ");
        }
        builder.append(value.trim());
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(separator, parts);
    }

    private static String dateRange(java.time.LocalDate start, java.time.LocalDate end) {
        if (start == null && end == null) {
            return "";
        }
        String startText = start == null ? "" : start.toString().substring(0, 7);
        String endText = end == null ? "至今" : end.toString().substring(0, 7);
        return startText + " ~ " + endText;
    }

    /** 逐页输出的简单排版器：自动换行、自动分页。 */
    private static final class PdfWriter implements AutoCloseable {

        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private float cursorY;

        private PdfWriter(PDDocument document, PDFont font) throws Exception {
            this.document = document;
            this.font = font;
            newPage();
        }

        private void newPage() throws Exception {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            cursorY = PAGE_HEIGHT - MARGIN;
        }

        private void ensureSpace(float needed) throws Exception {
            if (cursorY - needed < MARGIN) {
                newPage();
            }
        }

        private void heading(String text, float size) throws Exception {
            ensureSpace(size + 8);
            writeLine(text, size, 0f, false);
            cursorY -= 4f;
        }

        private void sectionTitle(String text) throws Exception {
            ensureSpace(28f);
            cursorY -= 6f;
            writeLine(text, 12f, 0f, true);
            cursorY -= 4f;
        }

        private void itemTitle(String text) throws Exception {
            if (text == null || text.isBlank()) {
                return;
            }
            writeWrapped(text, BODY_SIZE + 0.5f, 2f, true);
        }

        private void bullet(String text) throws Exception {
            writeWrapped("· " + text, BODY_SIZE, 2f, false);
        }

        private void paragraph(String text, float size, float extraGap) throws Exception {
            writeWrapped(text, size, extraGap, false);
        }

        private void writeWrapped(String text, float size, float extraGap, boolean emphasize) throws Exception {
            if (text == null || text.isBlank()) {
                return;
            }
            for (String raw : text.split("\\R")) {
                for (String line : wrap(raw, size, PAGE_WIDTH - MARGIN * 2)) {
                    ensureSpace(size + LINE_GAP);
                    writeLine(line, size, extraGap, emphasize);
                }
            }
            cursorY -= extraGap;
        }

        private void writeLine(String text, float size, float extraGap, boolean emphasize) throws Exception {
            float x = MARGIN;
            if (emphasize) {
                x = MARGIN + 28f;
            }
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(31 / 255f, 41 / 255f, 55 / 255f);
            stream.newLineAtOffset(x, cursorY);
            stream.showText(text == null ? "" : text);
            stream.endText();
            cursorY -= size + LINE_GAP + extraGap;
        }

        private void drawAvatar(String dataUrl) {
            if (dataUrl == null || !dataUrl.startsWith("data:image/")) {
                return;
            }
            try {
                int commaIndex = dataUrl.indexOf(',');
                if (commaIndex < 0) {
                    return;
                }
                byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(commaIndex + 1));
                PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, "avatar");
                float width = 64f;
                float height = 64f * image.getHeight() / Math.max(image.getWidth(), 1);
                stream.drawImage(image, PAGE_WIDTH - MARGIN - width, PAGE_HEIGHT - MARGIN - height, width, height);
            } catch (Exception ignored) {
                // 头像损坏时跳过，不影响正文导出
            }
        }

        /** 按中英文近似宽度换行，避免超出右边距。 */
        private static List<String> wrap(String text, float size, float maxWidth) {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            float width = 0f;
            for (int offset = 0; offset < text.length(); ) {
                int codePoint = text.codePointAt(offset);
                String ch = new String(Character.toChars(codePoint));
                float charWidth = Character.isIdeographic(codePoint) ? size : size * 0.56f;
                if (width + charWidth > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                    width = 0f;
                }
                line.append(ch);
                width += charWidth;
                offset += Character.charCount(codePoint);
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        @Override
        public void close() throws Exception {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }
    }
}
