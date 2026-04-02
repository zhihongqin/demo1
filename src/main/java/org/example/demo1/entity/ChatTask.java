package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_task")
public class ChatTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** UUID，前端轮询用的唯一标识 */
    private String taskId;

    /** 所属会话ID */
    private Long sessionId;

    /** 发起用户ID */
    private Long userId;

    /** 用户提问 */
    private String question;

    /** AI 回答（完成后写入） */
    private String answer;

    /**
     * 任务状态：0=处理中(PENDING)  1=完成(DONE)  2=失败(ERROR)
     */
    private Integer status;

    /** 失败原因 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
