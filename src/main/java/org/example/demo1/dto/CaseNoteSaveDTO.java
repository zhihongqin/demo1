package org.example.demo1.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CaseNoteSaveDTO {

    /**
     * 笔记正文；传空或仅空白表示删除该案例下的笔记
     */
    @Size(max = 10000, message = "笔记内容不能超过10000字")
    private String content;
}
