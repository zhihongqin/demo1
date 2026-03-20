package org.example.demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HotKeywordCreateDTO {

    @NotBlank(message = "关键词不能为空")
    @Size(max = 100, message = "关键词最长100字符")
    private String keyword;

    private Integer sortOrder;

    private Integer isPinned;

    private Integer isEnabled;
}
