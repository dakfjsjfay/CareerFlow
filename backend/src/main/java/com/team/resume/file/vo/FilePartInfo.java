package com.team.resume.file.vo;

/**
 * 本次分析中的单个文件信息。
 */
public record FilePartInfo(String fileName, String fileType, boolean truncated) {
}
