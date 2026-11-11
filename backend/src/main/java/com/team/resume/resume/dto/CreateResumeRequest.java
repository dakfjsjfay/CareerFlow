package com.team.resume.resume.dto;

import lombok.Data;

/**
 * 创建简历主体：名称可选，缺省为「我的简历」。
 */
@Data
public class CreateResumeRequest {

    private String resumeName;
}
