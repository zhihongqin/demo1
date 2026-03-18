package org.example.demo1.vo;

import lombok.Data;

/**
 * 案例字段补全结果 VO
 * 由 CaseEnrichAgent 从案例内容中提取
 */
@Data
public class CaseEnrichVO {

    /** 案件类型：1-民事，2-刑事，3-行政，4-商事 */
    private Integer caseType;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 涉及法律条文（简要，逗号分隔） */
    private String legalProvisions;

    /** 国家/地区 */
    private String country;

    /** 审理法院 */
    private String court;
}
