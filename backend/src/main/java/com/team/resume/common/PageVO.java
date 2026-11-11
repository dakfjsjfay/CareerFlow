package com.team.resume.common;

import java.util.List;

/**
 * 分页返回结构：{ total, page, size, records }。
 * MVP 的岗位数量只有 10~20 条，后续列表接口可复用该结构。
 */
public record PageVO<T>(long total, int page, int size, List<T> records) {
}
