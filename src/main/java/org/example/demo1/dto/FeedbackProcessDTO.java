package org.example.demo1.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackProcessDTO {

    /** 0-未处理 1-已处理 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Size(max = 1000, message = "处理说明不能超过1000字")
    private String adminReply;
}
