package com.team.resume.application.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 待跟进列表（主动提醒）。
 */
@Data
@AllArgsConstructor
public class FollowUpListVO {

    private int count;

    private List<FollowUpItemVO> items;
}
