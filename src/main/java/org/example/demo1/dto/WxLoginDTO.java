package org.example.demo1.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginDTO {

    @NotBlank(message = "微信code不能为空")
    private String code;

    /** 用户昵称（可选，小程序获取） */
    private String nickname;

    /** 头像URL（可选） */
    private String avatarUrl;
}
