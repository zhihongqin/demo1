package org.example.demo1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackSubmitDTO {

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 2000, message = "反馈内容不能超过2000字")
    private String content;

    @Size(max = 100, message = "联系方式不能超过100字")
    private String contact;

    @Size(max = 500, message = "客户端信息过长")
    private String clientInfo;
}
