package org.example.demo1.vo;

import lombok.Data;

@Data
public class CaseScoreVO {
    private Integer importanceScore;
    private Integer influenceScore;
    private Integer referenceScore;
    private Integer totalScore;
    private String scoreReason;
    private String scoreTags;
}
