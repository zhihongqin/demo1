package org.example.demo1.vo;

import lombok.Data;

@Data
public class UserVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String phone;
    private Integer role;
    private String token;
}
