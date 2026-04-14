package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话ID */
    private Long sessionId;

    /** 用户ID（冗余字段，方便按用户查询） */
    private Long userId;

    /** 角色：user-用户，assistant-AI */
    private String role;

    /** 消息内容 */
    private String content;

    /** 用户消息附件 URL（可选） */
    private String fileUrl;

    /** 用户消息附件名（可选） */
    private String fileName;

    /** 本次请求消耗的 token 数（仅 assistant 消息有效） */
    private Integer tokensUsed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer isDeleted;
}
