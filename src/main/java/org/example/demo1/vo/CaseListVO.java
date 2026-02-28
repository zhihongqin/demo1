package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class CaseListVO {

    private Long id;
    private String caseNo;
    private String titleZh;
    private String titleEn;
    private String caseReason;
    private Integer caseType;
    private String country;
    private String court;
    private LocalDate judgmentDate;
    private String summaryZh;
    private Integer importanceScore;
    private String keywords;
    private Integer aiStatus;
    private Integer viewCount;
    private Integer favoriteCount;
    private LocalDateTime createdAt;

    /** 当前用户是否已收藏 */
    private Boolean isFavorited;
}
