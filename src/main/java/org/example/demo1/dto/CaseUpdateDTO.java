package org.example.demo1.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 管理员手动修正案例内容的 DTO
 * 所有字段均为可选，传 null 则不覆盖对应字段
 */
@Data
public class CaseUpdateDTO {

    /** 案例编号 */
    private String caseNo;

    /** 案例标题（中文） */
    private String titleZh;

    /** 案例标题（英文） */
    private String titleEn;

    /** 案由 */
    private String caseReason;

    /** 案件类型：1-民事，2-刑事，3-行政，4-商事 */
    private Integer caseType;

    /** 所属国家/地区 */
    private String country;

    /** 审理法院 */
    private String court;

    /** 判决日期 */
    private LocalDate judgmentDate;

    /** 原文内容（英文） */
    private String contentEn;

    /** 翻译内容（中文） */
    private String contentZh;

    /** 争议焦点（AI生成，可修正） */
    private String disputeFocus;

    /** 判决结果（AI生成，可修正） */
    private String judgmentResult;

    /** 核心摘要（AI生成，可修正） */
    private String summaryZh;

    /** 重要性评分（AI生成，可修正，0-100） */
    private Integer importanceScore;

    /** 评分理由（AI生成，可修正） */
    private String scoreReason;

    /** 关键词（逗号分隔，AI生成，可修正） */
    private String keywords;

    /** 涉及法律条文 */
    private String legalProvisions;

    /** 案例发布来源 */
    private String source;

    /** 案例原始访问链接 */
    private String url;

    /** 原文 PDF 链接（日本裁判所等） */
    private String pdfUrl;
}
