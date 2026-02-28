package org.example.demo1.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("search_history")
public class SearchHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 搜索关键词 */
    private String keyword;

    /** 搜索类型：1-全文搜索，2-案由搜索，3-国家搜索 */
    private Integer searchType;

    /** 搜索结果数量 */
    private Integer resultCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
