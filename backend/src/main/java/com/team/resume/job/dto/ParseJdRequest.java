package com.team.resume.job.dto;

import lombok.Data;

/**
 * JD 解析请求（docs/api.md §8）。
 * jdContent 为空时，后端回退使用该岗位已保存的 jd_content。
 */
@Data
public class ParseJdRequest {

    private String jdContent;
}
