package org.example.demo1.dto;

import lombok.Data;

@Data
public class HotKeywordUpdateDTO {

    private Integer sortOrder;
    private Integer isPinned;
    private Integer isEnabled;
}
