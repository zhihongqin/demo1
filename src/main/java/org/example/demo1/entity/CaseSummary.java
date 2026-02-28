package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_summary")
public class CaseSummary {

    @TableId(type = IdType.AUTO)
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

    /** 错误信息 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
