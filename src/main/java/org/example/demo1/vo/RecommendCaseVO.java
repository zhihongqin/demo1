package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 推荐案例 VO（用于相似案例推荐接口）
 */
@Data
public class RecommendCaseVO {

    private Long id;
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
    private Integer viewCount;
    private Integer favoriteCount;

    /** 与目标案例的综合相似度 [0.0, 1.0] */
    private Double similarityScore;

    /** 相似度主要来源描述，如"关键词重合·案由相似" */
    private String similarityReason;
}
