package com.team.resume.application.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 投递记录（job_application）：关联岗位与投递时使用的简历版本。
 * 状态流转、提醒计时规则见 docs/database.md §4、§5。
 */
@Data
@TableName("job_application")
public class JobApplication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long jobId;

    /** 投递时使用的简历版本 */
    private Long resumeVersionId;

    /** ApplicationStatus 枚举名 */
    private String status;

    private LocalDateTime appliedAt;

    /** 下次跟进提醒时间；为空时按 updated_at + 3 天计算 */
    private LocalDateTime nextFollowUpAt;

    private LocalDateTime lastFollowUpAt;

    private LocalDateTime updatedAt;
}
