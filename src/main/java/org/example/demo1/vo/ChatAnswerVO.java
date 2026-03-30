package org.example.demo1.vo;

import lombok.Data;

@Data
public class ChatAnswerVO {

    /** AI 回答内容 */
    private String answer;

    /** 会话ID（与请求的 chatId 一致，方便前端更新本地状态） */
    private String chatId;

    /** 本次对话后该会话的累计消息条数 */
    private Integer messageCount;
}
