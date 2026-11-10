package com.team.resume.resume.vo;

/**
 * 导出结果：字节内容 + 建议文件名（不含路径）。
 */
public record ExportFile(byte[] bytes, String filename) {
}
