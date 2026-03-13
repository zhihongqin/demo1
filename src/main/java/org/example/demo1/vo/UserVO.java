package org.example.demo1.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private String username;
    private Integer role;
    private String token;

    /** 管理员视图额外字段 */
    private Integer status;
    private LocalDateTime createdAt;
}
