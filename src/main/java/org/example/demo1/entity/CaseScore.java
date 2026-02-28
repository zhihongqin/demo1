package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("case_score")
public class CaseScore {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联案例ID */
    private Long caseId;

    /** 重要性评分（0-100） */
    private Integer importanceScore;

    /** 影响力评分 */
    private Integer influenceScore;

    /** 参考价值评分 */
    private Integer referenceScore;

    /** 综合评分 */
    private Integer totalScore;

    /** 评分理由 */
    private String scoreReason;

    /** 评分标签（逗号分隔） */
    private String scoreTags;

    /** 评分状态：0-待评分，1-评分中，2-已完成，3-失败 */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
