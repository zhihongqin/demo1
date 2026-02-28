package org.example.demo1.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.demo1.common.result.Result;
import org.example.demo1.dto.WxLoginDTO;
import org.example.demo1.service.UserService;
import org.example.demo1.vo.UserVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 微信小程序登录
     * POST /api/auth/wx-login
     */
    @PostMapping("/wx-login")
    public Result<UserVO> wxLogin(@Valid @RequestBody WxLoginDTO dto) {
        return Result.success(userService.wxLogin(dto));
    }
}
