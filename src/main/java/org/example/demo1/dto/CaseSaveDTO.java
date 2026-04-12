package org.example.demo1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CaseSaveDTO {

    private Long id;

    /** 案例编号 */
    private String caseNo;

    @NotBlank(message = "英文标题不能为空")
    private String titleEn;

    /** 中文标题（可由AI翻译生成） */
    private String titleZh;

    /** 案件类型 */
    private Integer caseType;

    /** 所属国家/地区 */
    private String country;

    /** 审理法院 */
    private String court;

    /** 判决日期 */
    private LocalDate judgmentDate;

    @NotBlank(message = "英文原文不能为空")
    private String contentEn;

    /** 关键词 */
    private String keywords;

    /** 涉及法律条文 */
    private String legalProvisions;

    @NotBlank(message = "案例发布来源不能为空")
    private String source;

    @NotBlank(message = "案例原始访问链接不能为空")
    private String url;

    /** 原文 PDF 链接（日本裁判所等，可选） */
    private String pdfUrl;
}
