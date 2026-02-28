package org.example.demo1.controller;

import lombok.RequiredArgsConstructor;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.service.UserService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.UserVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前用户信息
     * GET /api/user/info
     */
    @GetMapping("/info")
    public Result<UserVO> getUserInfo() {
        Long userId = requireLogin();
        return Result.success(userService.getUserInfo(userId));
    }

    /**
     * 更新用户信息
     * PUT /api/user/info
     */
    @PutMapping("/info")
    public Result<Void> updateUserInfo(
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String avatarUrl) {
        Long userId = requireLogin();
        userService.updateUserInfo(userId, nickname, avatarUrl);
        return Result.success();
    }

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }
}
