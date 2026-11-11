package com.team.resume.job.dto;

import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * JD 解析请求：jdContent 可选。
 * 不传时解析岗位已保存的 jd_content；传入时先覆盖 JD 再解析，便于人工修正后重新生成要求。
 */
@Data
public class JdParseRequest {

    @Size(max = 20000, message = "JD 内容不能超过20000个字符")
    private String jdContent;
}
