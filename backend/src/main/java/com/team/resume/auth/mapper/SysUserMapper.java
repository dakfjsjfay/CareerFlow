package com.team.resume.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.resume.auth.entity.SysUser;

/**
 * 用户表 Mapper：继承 BaseMapper 后自动获得 insert/selectById/selectList 等能力，
 * 常规 CRUD 不需要手写 SQL。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
