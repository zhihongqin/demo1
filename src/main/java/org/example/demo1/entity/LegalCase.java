package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("legal_case")
public class LegalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    /** 争议焦点 */
    private String disputeFocus;

    /** 判决结果 */
    private String judgmentResult;

    /** 核心摘要（中文） */
    private String summaryZh;

    /** 重要性评分（0-100） */
    private Integer importanceScore;

    /** 评分理由 */
    private String scoreReason;

    /** 关键词（逗号分隔） */
    private String keywords;

    /** 涉及法律条文 */
    private String legalProvisions;

    /** 状态：0-待处理，1-AI处理中，2-处理完成，3-处理失败 */
    private Integer aiStatus;

    /** 查看次数 */
    private Integer viewCount;

    /** 收藏次数 */
    private Integer favoriteCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
