package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CaseDetailVO {

    private Long id;
    private String caseNo;
    private String titleZh;
    private String titleEn;
    private String caseReason;
    private Integer caseType;
    private String country;
    private String court;
    private LocalDate judgmentDate;

    private String contentEn;
    private String contentZh;
    private String disputeFocus;
    private String judgmentResult;
    private String summaryZh;

    private Integer importanceScore;
    private String scoreReason;
    private String keywords;
    private String legalProvisions;

    private Integer aiStatus;
    private Integer viewCount;
    private Integer favoriteCount;
    private LocalDateTime createdAt;

    /** 当前用户是否已收藏 */
    private Boolean isFavorited;

    /** 摘要提取结果 */
    private CaseSummaryVO summary;

    /** 评分详情 */
    private CaseScoreVO score;
}
