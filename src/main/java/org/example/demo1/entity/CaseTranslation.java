package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_translation")
public class CaseTranslation {

    @TableId(type = IdType.AUTO)
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

    /** FastGPT响应token数 */
    private Integer tokenUsed;

    /** 错误信息 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
