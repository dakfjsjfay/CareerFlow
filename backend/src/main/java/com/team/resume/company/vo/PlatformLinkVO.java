package com.team.resume.company.vo;

/**
 * 第三方招聘平台入口。
 * 均为外部搜索页，仅做跳转，不包含自动投递能力。
 */
public record PlatformLinkVO(String key, String label, String url, String note) {
}
