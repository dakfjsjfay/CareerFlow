package com.team.resume.file;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.team.resume.ai.AIService;
import com.team.resume.ai.dto.DocumentAnalysisResult;
import com.team.resume.common.BusinessException;
import com.team.resume.file.vo.FileAnalysisResponse;
import com.team.resume.file.vo.FilePartInfo;

/**
 * 文件分析业务（docs/api.md §15）：
 * 解析 1~6 个上传文件 → 合并文本 → 调用 AI → 返回候选信息。
 * ZIP 必须单独上传；本阶段不写数据库，D4 证据链确认后再入档。
 */
@Service
public class FileAnalysisService {

    private static final int MAX_FILES = 6;
    private static final int MAX_COMBINED_CHARS = 20_000;
    private static final long MAX_TOTAL_BYTES = 60L * 1024 * 1024;

    private static final Set<String> ANALYSIS_TYPES = Set.of("AUTO", "PROFILE", "PROJECT");
    private static final String CONFIRM_WARNING = "分析结果来自上传文件，需用户确认后才可写入档案";

    private final DocumentTextExtractor extractor;
    private final AIService aiService;

    public FileAnalysisService(DocumentTextExtractor extractor, AIService aiService) {
        this.extractor = extractor;
        this.aiService = aiService;
    }

    public FileAnalysisResponse analyze(List<MultipartFile> files, String analysisType, String jobDescription) {
        validateFiles(files);
        String type = normalizeType(analysisType);

        long totalBytes = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new BusinessException(400, "文件总大小不能超过 60MB");
        }

        List<ExtractedDocument> documents = new ArrayList<>();
        for (MultipartFile file : files) {
            documents.add(extractor.extract(file));
        }

        boolean truncated = documents.stream().anyMatch(ExtractedDocument::truncated);
        List<String> warnings = new ArrayList<>();
        for (ExtractedDocument document : documents) {
            String prefix = documents.size() > 1 ? document.fileName() + "：" : "";
            for (String warning : document.warnings()) {
                warnings.add(prefix + warning);
            }
        }

        String combinedText = combineTexts(documents);
        if (combinedText.length() > MAX_COMBINED_CHARS) {
            combinedText = combinedText.substring(0, MAX_COMBINED_CHARS);
            truncated = true;
            warnings.add("多个文件正文合计超过 20000 字符，已截断");
        }

        String fileLabel = documents.size() == 1
                ? documents.get(0).fileName()
                : documents.stream().map(ExtractedDocument::fileName).collect(Collectors.joining("、"));

        DocumentAnalysisResult result = aiService.analyzeDocument(
                fileLabel, type, jobDescription, combinedText);

        warnings.addAll(result.warnings());
        warnings.add(CONFIRM_WARNING);

        List<FilePartInfo> fileParts = documents.stream()
                .map(d -> new FilePartInfo(d.fileName(), d.fileType(), d.truncated()))
                .toList();

        String fileName = documents.size() == 1 ? documents.get(0).fileName() : fileLabel;
        String fileType = documents.size() == 1 ? documents.get(0).fileType() : "MULTI";

        return new FileAnalysisResponse(
                fileParts,
                fileName,
                fileType,
                type,
                truncated,
                result.summary(),
                result.extractedInfo(),
                result.strengths(),
                result.resumeSuggestions(),
                result.jobGapSuggestions(),
                result.missingFields(),
                new ArrayList<>(new LinkedHashSet<>(warnings)));
    }

    private static void validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }
        if (files.size() > MAX_FILES) {
            throw new BusinessException(400, "一次最多上传 " + MAX_FILES + " 个文件");
        }
        boolean hasZip = files.stream().anyMatch(FileAnalysisService::isZip);
        if (hasZip && files.size() > 1) {
            throw new BusinessException(400, "ZIP 请单独上传分析，不要和其他文件混合");
        }
    }

    private static boolean isZip(MultipartFile file) {
        String name = file.getOriginalFilename();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static String combineTexts(List<ExtractedDocument> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            ExtractedDocument document = documents.get(i);
            if (documents.size() > 1) {
                sb.append("===== 文件 ").append(i + 1).append("：")
                        .append(document.fileName()).append(" =====\n");
            }
            sb.append(document.text()).append("\n\n");
        }
        return sb.toString().trim();
    }

    private static String normalizeType(String analysisType) {
        if (analysisType == null || analysisType.isBlank()) {
            return "AUTO";
        }
        String type = analysisType.trim().toUpperCase(Locale.ROOT);
        return ANALYSIS_TYPES.contains(type) ? type : "AUTO";
    }
}
