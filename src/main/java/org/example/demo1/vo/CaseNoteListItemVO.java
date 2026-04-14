package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CaseNoteListItemVO {

    private Long caseId;
    private String titleZh;
    private String titleEn;
    private String contentPreview;
    private LocalDateTime updatedAt;
}
