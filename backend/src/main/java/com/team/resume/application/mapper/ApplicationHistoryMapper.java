package com.team.resume.application.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.resume.application.entity.ApplicationHistory;

@Mapper
public interface ApplicationHistoryMapper extends BaseMapper<ApplicationHistory> {
}
