package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CrawlJobRecordVO {

    private Long id;
    private String crawlType;
    /** 中文展示用 */
    private String crawlTypeLabel;
    private String paramsJson;
    private Integer status;
    private String statusLabel;
    private Integer savedCount;
    private String errorMessage;
    private Long startedBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
