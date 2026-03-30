package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ChatSessionVO {

    /** 会话数据库ID */
    private Long id;

    /** 会话标识（chatId） */
    private String chatId;

    /** 会话标题 */
    private String title;

    /** 消息总条数 */
    private Integer messageCount;

    /** 最近一次用户提问（预览） */
    private String lastQuestion;

    /** 最近活跃时间 */
    private LocalDateTime updatedAt;

    /** 消息列表（查看详情时才填充） */
    private List<ChatMessageVO> messages;

    @Data
    public static class ChatMessageVO {
        private Long id;
        /** user / assistant */
        private String role;
        private String content;
        private LocalDateTime createdAt;
    }
}
