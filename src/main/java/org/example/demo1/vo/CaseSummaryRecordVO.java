package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 案例摘要记录（管理员视图）
 */
@Data
public class CaseSummaryRecordVO {

    private Long id;

    /** 关联案例ID */
    private Long caseId;

    /** 案由摘要 */
    private String caseReason;

    /** 争议焦点 */
    private String disputeFocus;

    /** 判决结果摘要 */
    private String judgmentResult;

    /** 核心要点 */
    private String keyPoints;

    /** 提取状态：0-待提取，1-提取中，2-已完成，3-失败 */
    private Integer status;

    /** 失败时的错误信息 */
    private String errorMsg;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
