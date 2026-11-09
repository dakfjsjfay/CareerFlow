package com.team.resume.file;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.team.resume.common.BusinessException;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

/**
 * 本地图片 OCR（Tess4J + tesseract 原生库）。
 * 语言包随应用打包，启动时复制到临时目录；图片仅在内存中处理，不落盘。
 */
@Component
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    /** 超大图片先缩放，降低 OCR 耗时与内存占用。 */
    private static final int MAX_DIMENSION = 2400;

    private final String dataPath;
    private final String language;

    public OcrService() {
        Path dataDir = Paths.get(System.getProperty("java.io.tmpdir"), "careerflow-ocr-tessdata");
        try {
            Files.createDirectories(dataDir);
            boolean hasEng = copyResource("tessdata/eng.traineddata", dataDir.resolve("eng.traineddata"));
            boolean hasChi = copyResource("tessdata/chi_sim.traineddata", dataDir.resolve("chi_sim.traineddata"));
            this.language = hasChi ? "chi_sim+eng" : "eng";
            this.dataPath = dataDir.toString();
            if (!hasEng) {
                log.warn("OCR 语言包缺失：tessdata/eng.traineddata");
            }
        } catch (IOException e) {
            throw new IllegalStateException("初始化 OCR 语言包失败", e);
        }
    }

    public String recognize(byte[] imageBytes) {
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {
            throw new BusinessException(400, "图片读取失败，请确认文件未损坏");
        }
        if (image == null) {
            throw new BusinessException(400, "无法识别图片格式，请使用 PNG/JPG/BMP/GIF");
        }
        return recognize(image);
    }

    public String recognize(BufferedImage source) {
        BufferedImage image = resizeIfNeeded(source);
        try {
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(dataPath);
            tesseract.setLanguage(language);
            String text = tesseract.doOCR(image);
            return text == null ? "" : text.trim();
        } catch (TesseractException | LinkageError e) {
            log.warn("OCR 识别失败", e);
            throw new BusinessException(400, "图片识别失败，请确认图片清晰，或改用 Word/文本/PDF");
        }
    }

    private static BufferedImage resizeIfNeeded(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int max = Math.max(width, height);
        if (max <= MAX_DIMENSION) {
            return source;
        }
        double scale = (double) MAX_DIMENSION / max;
        int targetWidth = Math.max(1, (int) (width * scale));
        int targetHeight = Math.max(1, (int) (height * scale));
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static boolean copyResource(String resource, Path target) throws IOException {
        ClassPathResource classPathResource = new ClassPathResource(resource);
        if (!classPathResource.exists()) {
            return false;
        }
        try (InputStream in = classPathResource.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }
}
