package com.team.resume.resume.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.resume.resume.entity.Resume;

public interface ResumeMapper extends BaseMapper<Resume> {

    /**
     * 生成新版本时锁定简历行，保证同一 resume 的版本号串行分配。
     */
    @Select("SELECT * FROM resume WHERE id = #{id} FOR UPDATE")
    Resume selectByIdForUpdate(@Param("id") Long id);
}
