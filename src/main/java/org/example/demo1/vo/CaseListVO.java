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
    private String source;
    private String url;
    private Integer aiStatus;
    private Integer viewCount;
    private Integer favoriteCount;
    private LocalDateTime createdAt;

    /** 当前用户是否已收藏 */
    private Boolean isFavorited;

    /** 是否已逻辑删除：0-正常，1-已删除 */
    private Integer isDeleted;
}
