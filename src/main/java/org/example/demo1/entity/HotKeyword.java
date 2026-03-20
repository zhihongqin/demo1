package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hot_keyword")
public class HotKeyword {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyword;

    private Integer searchCount;

    private Integer sortOrder;

    /** 1=置顶，定时任务不自动下线 */
    private Integer isPinned;

    /** 1=对用户展示 */
    private Integer isEnabled;

    /** 0=统计同步 1=管理员手动 */
    private Integer origin;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
