package com.team.resume.common;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一分页返回结构（docs/api.md §1：items/total/page/size）。
 * job、application 等列表接口复用。
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;

    private long total;

    private long page;

    private long size;
}
