package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CaseNoteVO {

    /** 笔记正文，无笔记时为空字符串 */
    private String content;

    private LocalDateTime updatedAt;
}
