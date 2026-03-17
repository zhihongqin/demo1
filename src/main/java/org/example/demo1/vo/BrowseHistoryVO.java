package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BrowseHistoryVO {

    /** 浏览记录ID */
    private Long id;

    /** 案例ID */
    private Long caseId;

    /** 案例标题（中文） */
    private String titleZh;

    /** 案例标题（英文） */
    private String titleEn;

    /** 案由 */
    private String caseReason;

    /** 所属国家/地区 */
    private String country;

    /** 审理法院 */
    private String court;

    /** 案件类型：1-民事，2-刑事，3-行政，4-商事 */
    private Integer caseType;

    /** 重要性评分 */
    private Integer importanceScore;

    /** 浏览时间 */
    private LocalDateTime createdAt;
}
