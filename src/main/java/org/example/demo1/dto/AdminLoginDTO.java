package org.example.demo1.dto;

import lombok.Data;

@Data
public class AdminLoginDTO {

    /** 登录账号 */
    private String username;

    /** 登录密码（明文） */
    private String password;
}
