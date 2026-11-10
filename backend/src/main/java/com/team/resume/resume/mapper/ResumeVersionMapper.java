package com.team.resume.resume.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.resume.resume.entity.ResumeVersion;

public interface ResumeVersionMapper extends BaseMapper<ResumeVersion> {

    @Select("SELECT COALESCE(MAX(version_number), 0) FROM resume_version WHERE resume_id = #{resumeId}")
    int selectMaxVersionNumber(@Param("resumeId") Long resumeId);
}
