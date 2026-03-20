package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HotKeywordVO {
    private Long id;
    private String keyword;
    private Integer searchCount;
    private Integer sortOrder;
    private Integer isPinned;
    private Integer isEnabled;
    private Integer origin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
