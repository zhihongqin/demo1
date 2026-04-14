package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_feedback")
public class SystemFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    private String contact;

    private String clientInfo;

    /** 0-未处理 1-已处理 */
    private Integer status;

    private String adminReply;

    private LocalDateTime processedAt;

    private Long processedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
