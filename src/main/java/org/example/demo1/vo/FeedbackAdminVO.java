package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FeedbackAdminVO {

    private Long id;
    private Long userId;
    private String userNickname;
    private String content;
    private String contact;
    private String clientInfo;
    /** 0-未处理 1-已处理 */
    private Integer status;
    private String adminReply;
    private LocalDateTime processedAt;
    private Long processedBy;
    private String processorNickname;
    private LocalDateTime createdAt;
}
