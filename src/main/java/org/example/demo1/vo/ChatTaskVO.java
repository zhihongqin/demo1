package org.example.demo1.vo;

import lombok.Data;

/**
 * 异步问答任务状态 VO
 * <ul>
 *   <li>status = "PENDING"：AI 处理中，前端继续轮询</li>
 *   <li>status = "DONE"   ：处理完成，answer 字段有值</li>
 *   <li>status = "ERROR"  ：处理失败，errorMsg 字段有值</li>
 * </ul>
 */
@Data
public class ChatTaskVO {

    /** 任务 UUID，前端轮询使用 */
    private String taskId;

    /** 会话标识，前端后续提问需携带 */
    private String chatId;

    /** PENDING / DONE / ERROR */
    private String status;

    /** AI 回答内容（status=DONE 时有值） */
    private String answer;

    /** 失败原因（status=ERROR 时有值） */
    private String errorMsg;
}
