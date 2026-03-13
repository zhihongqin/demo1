package org.example.demo1.dto;

import lombok.Data;

@Data
public class SetAdminDTO {

    /** 目标角色：0-普通用户，1-管理员 */
    private Integer role;

    /** 管理员登录账号（role=1 时必填） */
    private String username;

    /** 管理员登录密码（明文，role=1 时必填） */
    private String password;
}
