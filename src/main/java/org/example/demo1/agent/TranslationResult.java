package org.example.demo1.agent;

import lombok.Data;

/**
 * 翻译结果：包含翻译正文及同一会话中顺带提取的结构化字段。
 * 适用于美国案例（{@link TranslationAgent}）和日本案例（{@link JapanTranslationAgent}），
 * 翻译完成后无需再单独调用字段补全接口。
 */
@Data
public class TranslationResult {

    /** 翻译后的中文正文 */
    private String contentZh;

    /** 案件类型：1-民事，2-刑事，3-行政，4-商事；提取失败时为 null */
    private Integer caseType;

    /** 案由（简短描述） */
    private String caseReason;

    /** 关键词（英文逗号分隔） */
    private String keywords;

    /** 涉及法律条文（英文逗号分隔） */
    private String legalProvisions;

    /** 所属国家/地区（中文名称） */
    private String country;

    /** 审理法院（中文译名，含英文原名） */
    private String court;
}
