package org.example.demo1.dto;

import lombok.Data;

@Data
public class AdminChangePasswordDTO {

    /** 旧密码（明文） */
    private String oldPassword;

    /** 新密码（明文） */
    private String newPassword;
}
