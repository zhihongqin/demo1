package org.example.demo1.dto;

import lombok.Data;

@Data
public class FeedbackQueryDTO {

    /** 0-未处理 1-已处理，不传则全部 */
    private Integer status;

    /** 反馈正文关键词 */
    private String keyword;

    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
