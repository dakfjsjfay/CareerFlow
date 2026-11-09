package com.team.resume.file;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.team.resume.common.BusinessException;

/**
 * 上传文件文本抽取（docs/api.md §15）。
 * <p>
 * 支持：docx、pptx、txt/md 及常见代码文本文件、zip（递归分析文本/Word/PPT 文件）。
 * 安全约束：不落盘、不解压到磁盘、限制 zip 条目数/单条大小/总解压大小。
 */
@Component
public class DocumentTextExtractor {

    private static final int MAX_PROMPT_CHARS = 20_000;
    private static final int MAX_ZIP_ENTRIES = 200;
    private static final int MAX_ZIP_OCR_ITEMS = 3;
    private static final long MAX_ZIP_ENTRY_BYTES = 2L * 1024 * 1024;
    private static final long MAX_ZIP_TOTAL_BYTES = 20L * 1024 * 1024;

    private static final Set<String> OFFICE_EXTENSIONS = Set.of("docx", "pptx");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "bmp", "gif");

    /** 直接上传或 zip 内可识别的文本/代码文件。 */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "log", "csv",
            "json", "xml", "yml", "yaml", "properties",
            "java", "kt", "py", "js", "ts", "jsx", "tsx", "vue",
            "html", "htm", "css", "scss", "less",
            "sql", "sh", "bat", "gradle",
            "c", "h", "cpp", "hpp", "go", "rs", "php", "rb");

    static {
        // 防 zip 炸弹：限制极端压缩比与单条目解压大小
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(100L * 1024 * 1024);
    }

    private final OcrService ocrService;

    public DocumentTextExtractor(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public ExtractedDocument extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String ext = extensionOf(fileName);
        if (ext.isEmpty()) {
            throw new BusinessException(400, "无法识别文件类型");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(400, "读取上传文件失败");
        }

        List<String> warnings = new ArrayList<>();
        boolean truncated = false;
        String text;
        try {
            if ("docx".equals(ext)) {
                text = extractDocx(bytes);
            } else if ("pptx".equals(ext)) {
                text = extractPptx(bytes);
            } else if ("pdf".equals(ext)) {
                text = extractPdf(bytes, warnings);
            } else if (IMAGE_EXTENSIONS.contains(ext)) {
                text = ocrService.recognize(bytes);
            } else if ("zip".equals(ext)) {
                ZipText zipText = extractZip(bytes, warnings);
                text = zipText.text();
                truncated = zipText.truncated();
            } else if (TEXT_EXTENSIONS.contains(ext)) {
                text = decodeText(bytes);
            } else {
                throw new BusinessException(400, "不支持的文件类型: ." + ext);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "文件解析失败，请确认文件未损坏");
        }

        text = text.trim();
        if (text.isEmpty()) {
            throw new BusinessException(400, "文件中没有可分析的文本");
        }
        if (text.length() > MAX_PROMPT_CHARS) {
            text = text.substring(0, MAX_PROMPT_CHARS);
            truncated = true;
            warnings.add("正文超过 20000 字符，已截取前 20000 字符");
        }
        return new ExtractedDocument(fileName, ext.toUpperCase(Locale.ROOT), text, truncated, warnings);
    }

    private static String extractDocx(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendLine(sb, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        List<String> cells = new ArrayList<>();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            cells.add(cell.getText());
                        }
                        appendLine(sb, String.join(" | ", cells));
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String extractPptx(byte[] bytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            int slideIndex = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                appendLine(sb, "【幻灯片 " + slideIndex++ + "】");
                for (XSLFShape shape : slide.getShapes()) {
                    appendShape(sb, shape);
                }
            }
        }
        return sb.toString();
    }

    private static void appendShape(StringBuilder sb, XSLFShape shape) {
        if (shape instanceof XSLFTextShape textShape) {
            appendLine(sb, textShape.getText());
        } else if (shape instanceof XSLFTable table) {
            for (XSLFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XSLFTableCell cell : row.getCells()) {
                    cells.add(cell.getText());
                }
                appendLine(sb, String.join(" | ", cells));
            }
        } else if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                appendShape(sb, child);
            }
        }
    }

    /**
     * PDF：优先抽取文本层；没有文本层（扫描件）时渲染前 5 页做 OCR。
     */
    private String extractPdf(byte[] bytes, List<String> warnings) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text != null && text.replaceAll("\\s", "").length() >= 20) {
                return text;
            }

            int totalPages = document.getNumberOfPages();
            int pages = Math.min(totalPages, 5);
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 150);
                String ocr = ocrService.recognize(image);
                if (!ocr.isBlank()) {
                    sb.append("【第 ").append(i + 1).append(" 页】\n").append(ocr).append('\n');
                }
            }
            if (totalPages > pages) {
                warnings.add("PDF 共 " + totalPages + " 页，仅 OCR 前 " + pages + " 页");
            }
            return sb.toString();
        }
    }

    private ZipText extractZip(byte[] bytes, List<String> warnings) throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean truncated = false;
        int entryCount = 0;
        int heavyCount = 0;
        long totalBytes = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                // 不解压到磁盘，但依然跳过可疑路径，避免未来扩展成落盘实现时出现路径穿越
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    warnings.add("已跳过可疑路径: " + name);
                    continue;
                }
                String ext = extensionOf(name);
                boolean heavy = IMAGE_EXTENSIONS.contains(ext) || "pdf".equals(ext);
                if (!TEXT_EXTENSIONS.contains(ext) && !OFFICE_EXTENSIONS.contains(ext) && !heavy) {
                    continue;
                }
                if (heavy && ++heavyCount > MAX_ZIP_OCR_ITEMS) {
                    warnings.add("压缩包内图片/PDF 较多，仅识别前 " + MAX_ZIP_OCR_ITEMS + " 个");
                    continue;
                }
                if (++entryCount > MAX_ZIP_ENTRIES) {
                    warnings.add("压缩包文件过多，仅分析前 " + MAX_ZIP_ENTRIES + " 个文件");
                    truncated = true;
                    break;
                }
                byte[] content = readLimited(zis, MAX_ZIP_ENTRY_BYTES);
                totalBytes += content.length;
                if (totalBytes > MAX_ZIP_TOTAL_BYTES) {
                    warnings.add("压缩包解压内容过大，已停止分析");
                    truncated = true;
                    break;
                }
                String text;
                try {
                    if ("docx".equals(ext)) {
                        text = extractDocx(content);
                    } else if ("pptx".equals(ext)) {
                        text = extractPptx(content);
                    } else if ("pdf".equals(ext)) {
                        text = extractPdf(content, warnings);
                    } else if (IMAGE_EXTENSIONS.contains(ext)) {
                        text = ocrService.recognize(content);
                    } else {
                        text = decodeText(content);
                    }
                } catch (Exception e) {
                    warnings.add("解析失败，已跳过: " + name);
                    continue;
                }
                if (!text.isBlank()) {
                    sb.append("===== ").append(name).append(" =====\n")
                            .append(text.trim()).append("\n\n");
                    if (sb.length() > MAX_PROMPT_CHARS * 2L) {
                        warnings.add("压缩包文本过多，已截断");
                        truncated = true;
                        break;
                    }
                }
            }
        }
        return new ZipText(sb.toString(), truncated);
    }

    private static byte[] readLimited(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (total + read > limit) {
                out.write(buffer, 0, (int) (limit - total));
                break;
            }
            out.write(buffer, 0, read);
            total += read;
        }
        return out.toByteArray();
    }

    private static String decodeText(byte[] bytes) {
        String utf8 = tryDecode(bytes, StandardCharsets.UTF_8);
        String text = utf8 != null ? utf8 : tryDecode(bytes, Charset.forName("GBK"));
        if (text == null) {
            text = new String(bytes, StandardCharsets.UTF_8);
        }
        // 去掉 UTF-8 BOM
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        return text;
    }

    private static String tryDecode(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static void appendLine(StringBuilder sb, String line) {
        if (line != null && !line.isBlank()) {
            sb.append(line.trim()).append('\n');
        }
    }

    private static String sanitizeFileName(String originalName) {
        String name = originalName == null ? "upload" : originalName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name.isBlank() ? "upload" : name;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1
                ? ""
                : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private record ZipText(String text, boolean truncated) {
    }
}
