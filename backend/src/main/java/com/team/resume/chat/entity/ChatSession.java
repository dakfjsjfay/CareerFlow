package com.team.resume.chat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 对话会话（chat_session）：一个用户可以有多个建档会话。
 */
@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /** ACTIVE / CLOSED */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
