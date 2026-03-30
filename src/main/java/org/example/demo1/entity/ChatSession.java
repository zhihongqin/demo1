package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 会话标识（小程序端生成，传给 FastGPT 维持多轮上下文） */
    private String chatId;

    /** 会话标题（取首条提问截断） */
    private String title;

    /** 消息总条数（含用户 + AI） */
    private Integer messageCount;

    /** 最近一次用户提问（列表预览用） */
    private String lastQuestion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
