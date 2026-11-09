package com.team.resume.profile.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.resume.profile.entity.ProfileEvidence;

/**
 * 证据 Mapper。
 * markProcessed 使用「PENDING 条件更新」防止并发重复确认：
 * 只有仍处于 PENDING 且属于当前用户的证据才会被更新，返回 0 表示已被处理。
 */
public interface ProfileEvidenceMapper extends BaseMapper<ProfileEvidence> {

    @Update("UPDATE profile_evidence "
            + "SET confirmation_status = #{status}, entity_id = #{entityId}, updated_at = NOW() "
            + "WHERE id = #{id} AND user_id = #{userId} AND confirmation_status = 'PENDING'")
    int markProcessed(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("status") String status,
                      @Param("entityId") Long entityId);
}
