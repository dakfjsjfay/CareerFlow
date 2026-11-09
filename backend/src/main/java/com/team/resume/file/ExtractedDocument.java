package com.team.resume.file;

import java.util.List;

/**
 * 从上传文件中抽取出的文本。
 * text 已按长度截断；文件原件不落盘。
 */
public record ExtractedDocument(String fileName, String fileType, String text,
                                boolean truncated, List<String> warnings) {
}
