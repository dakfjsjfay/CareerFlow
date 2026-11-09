package com.team.resume.file;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team.resume.common.Result;
import com.team.resume.file.vo.FileAnalysisResponse;

/**
 * 文件分析接口（docs/api.md §15）。
 * 需要登录；文件只在内存中解析，不落盘。
 */
@RestController
@RequestMapping("/api/files")
public class FileAnalysisController {

    private final FileAnalysisService fileAnalysisService;

    public FileAnalysisController(FileAnalysisService fileAnalysisService) {
        this.fileAnalysisService = fileAnalysisService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileAnalysisResponse> analyze(
            @RequestPart("file") List<MultipartFile> files,
            @RequestParam(required = false, defaultValue = "AUTO") String analysisType,
            @RequestParam(required = false) String jobDescription) {
        return Result.ok(fileAnalysisService.analyze(files, analysisType, jobDescription));
    }
}
