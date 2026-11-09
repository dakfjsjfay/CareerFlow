package com.team.resume.profile.service;

/**
 * 一条可用于证据归属的用户消息。
 * 调用方按时间正序传入（最后一条是当前消息），工厂从后向前查找最近的原话依据。
 */
public record EvidenceSource(Long messageId, String content) {
}
