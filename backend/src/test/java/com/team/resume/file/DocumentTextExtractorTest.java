package com.team.resume.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.team.resume.common.BusinessException;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor(new OcrService());

    @Test
    void extractsChineseTextFromDocx() throws Exception {
        byte[] bytes = createDocx("我是计算机专业大二学生，熟悉 Java 和 MySQL。");

        ExtractedDocument document = extractor.extract(
                new MockMultipartFile("file", "resume.docx", "application/octet-stream", bytes));

        assertThat(document.fileType()).isEqualTo("DOCX");
        assertThat(document.text()).contains("计算机专业");
        assertThat(document.text()).contains("Java 和 MySQL");
        assertThat(document.truncated()).isFalse();
    }

    @Test
    void extractsTextFromPptx() throws Exception {
        byte[] bytes = createPptx("项目：学生信息管理系统\n技术栈：Java、MySQL");

        ExtractedDocument document = extractor.extract(
                new MockMultipartFile("file", "project.pptx", "application/octet-stream", bytes));

        assertThat(document.fileType()).isEqualTo("PPTX");
        assertThat(document.text()).contains("学生信息管理系统");
        assertThat(document.text()).contains("Java、MySQL");
    }

    @Test
    void extractsTextAndCodeFilesFromZip() throws Exception {
        byte[] bytes = createZip();

        ExtractedDocument document = extractor.extract(
                new MockMultipartFile("file", "project.zip", "application/zip", bytes));

        assertThat(document.fileType()).isEqualTo("ZIP");
        assertThat(document.text()).contains("src/Main.java");
        assertThat(document.text()).contains("public class Main");
        assertThat(document.text()).contains("README.md");
        assertThat(document.text()).contains("学生管理系统");
    }

    @Test
    void extractsTextFromPdf() throws Exception {
        byte[] bytes = createPdf("Java MySQL resume project 2026");

        ExtractedDocument document = extractor.extract(
                new MockMultipartFile("file", "resume.pdf", "application/pdf", bytes));

        assertThat(document.fileType()).isEqualTo("PDF");
        assertThat(document.text()).contains("Java MySQL");
        assertThat(document.text()).contains("2026");
    }

    @Test
    void ocrExtractsTextFromImage() throws Exception {
        byte[] bytes = createImage("HELLO RESUME 2026");

        ExtractedDocument document = extractor.extract(
                new MockMultipartFile("file", "resume.png", "image/png", bytes));

        assertThat(document.fileType()).isEqualTo("PNG");
        assertThat(document.text().toUpperCase()).contains("HELLO");
    }

    @Test
    void decodesGbkTextFile() {
        byte[] bytes = "中文项目说明：使用 MyBatis 操作 MySQL".getBytes(Charset.forName("GBK"));

        ExtractedDocument document = extractor.extract(
                new MockMultipartFile("file", "说明.txt", "text/plain", bytes));

        assertThat(document.text()).contains("中文项目说明");
        assertThat(document.text()).contains("MyBatis");
    }

    @Test
    void rejectsUnsupportedFileType() {
        assertThatThrownBy(() -> extractor.extract(
                new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3})))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    @Test
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> extractor.extract(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(400);
    }

    private static byte[] createDocx(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            document.write(out);
        }
        return out.toByteArray();
    }

    private static byte[] createPptx(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XMLSlideShow slideShow = new XMLSlideShow()) {
            XSLFSlide slide = slideShow.createSlide();
            slide.createTextBox().setText(text);
            slideShow.write(out);
        }
        return out.toByteArray();
    }

    private static byte[] createPdf(String text) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(50, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
        }
        return out.toByteArray();
    }

    private static byte[] createImage(String text) throws IOException {
        BufferedImage image = new BufferedImage(900, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 900, 200);
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("SansSerif", Font.BOLD, 48));
            graphics.drawString(text, 30, 120);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] createZip() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("src/Main.java"));
            zip.write("public class Main { }".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("# 学生管理系统\n使用 Java 和 MySQL。".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
