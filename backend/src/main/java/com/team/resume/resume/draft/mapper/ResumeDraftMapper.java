package com.team.resume.resume.draft.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team.resume.resume.draft.entity.ResumeDraft;

@Mapper
public interface ResumeDraftMapper extends BaseMapper<ResumeDraft> {

    /**
     * 带 revision 乐观锁的全量保存；返回 0 表示被其他页面抢先保存或草稿已不可编辑。
     */
    @Update("""
            UPDATE resume_draft
               SET current_step = #{currentStep},
                   content = #{content},
                   draft_name = COALESCE(#{draftName}, draft_name),
                   revision = revision + 1,
                   last_saved_at = NOW(),
                   updated_at = NOW()
             WHERE id = #{id}
               AND user_id = #{userId}
               AND revision = #{revision}
               AND status = 'DRAFT'
            """)
    int updateContent(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("revision") Integer revision,
                      @Param("currentStep") Integer currentStep,
                      @Param("content") String content,
                      @Param("draftName") String draftName);

    @Update("""
            UPDATE resume_draft
               SET status = #{status},
                   resume_id = COALESCE(#{resumeId}, resume_id),
                   current_step = #{currentStep},
                   updated_at = NOW()
             WHERE id = #{id}
               AND user_id = #{userId}
               AND status = 'DRAFT'
            """)
    int updateStatus(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("resumeId") Long resumeId,
                     @Param("currentStep") Integer currentStep);

    @Select("SELECT * FROM resume_draft WHERE id = #{id} FOR UPDATE")
    ResumeDraft selectByIdForUpdate(@Param("id") Long id);
}
