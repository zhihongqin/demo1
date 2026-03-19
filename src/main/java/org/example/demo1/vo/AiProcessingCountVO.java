package org.example.demo1.vo;

import lombok.Data;

/**
 * AI 处理中任务数量统计
 */
@Data
public class AiProcessingCountVO {

    /** 翻译中的任务数 */
    private long translating;

    /** 摘要提取中的任务数 */
    private long summarizing;

    /** 评分中的任务数 */
    private long scoring;

    /** 三张表处理中任务总数 */
    private long total;
}
