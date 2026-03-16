package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案例翻译记录（管理员视图）
 */
@Data
public class CaseTranslationRecordVO {

    private Long id;

    /** 关联案例ID */
    private Long caseId;

    /** 原文语言 */
    private String sourceLang;

    /** 目标语言 */
    private String targetLang;

    /** 翻译后内容 */
    private String translatedContent;

    /** 翻译状态：0-待翻译，1-翻译中，2-已完成，3-失败 */
    private Integer status;

    /** 使用的AI模型 */
    private String aiModel;

    /** 消耗的 token 数 */
    private Integer tokenUsed;

    /** 失败时的错误信息 */
    private String errorMsg;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
