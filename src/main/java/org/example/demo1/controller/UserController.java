package org.example.demo1.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.Result;
import org.example.demo1.common.result.ResultCode;
import org.example.demo1.dto.AdminChangePasswordDTO;
import org.example.demo1.dto.AdminLoginDTO;
import org.example.demo1.dto.SetAdminDTO;
import org.example.demo1.entity.User;
import org.example.demo1.service.UserService;
import org.example.demo1.util.UserContext;
import org.example.demo1.vo.UserVO;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ─── 普通用户接口 ─────────────────────────────────────────────────────

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
     * 更新当前用户信息
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

    // ─── 管理员认证接口（无需登录态） ────────────────────────────────────────

    /**
     * 管理员账号密码登录
     * POST /api/user/admin/login
     */
    @PostMapping("/admin/login")
    public Result<UserVO> adminLogin(@RequestBody AdminLoginDTO dto) {
        return Result.success(userService.adminLogin(dto));
    }

    /**
     * 管理员退出登录
     * POST /api/user/admin/logout
     * 前端清除本地 Token 即可，服务端无状态；此接口仅作语义完整性保留。
     */
    @PostMapping("/admin/logout")
    public Result<Void> adminLogout() {
        requireAdmin();
        Long userId = UserContext.getUserId();
        log.info("管理员退出登录: userId={}", userId);
        return Result.success();
    }

    /**
     * 管理员修改自己的密码
     * PUT /api/user/admin/password
     */
    @PutMapping("/admin/password")
    public Result<Void> adminChangePassword(@RequestBody AdminChangePasswordDTO dto) {
        Long userId = requireAdmin();
        userService.adminChangePassword(userId, dto);
        return Result.success();
    }

    // ─── 管理员用户管理接口 ───────────────────────────────────────────────

    /**
     * 分页查询用户列表
     * GET /api/user/admin/list?page=1&pageSize=20&keyword=xxx
     */
    @GetMapping("/admin/list")
    public Result<IPage<UserVO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        requireAdmin();
        return Result.success(userService.listUsers(page, pageSize, keyword));
    }

    /**
     * 禁用或启用用户
     * PUT /api/user/admin/{userId}/status?status=1
     */
    @PutMapping("/admin/{userId}/status")
    public Result<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Integer status) {
        requireAdmin();
        userService.updateUserStatus(userId, status);
        return Result.success();
    }

    /**
     * 设置或取消管理员角色
     * PUT /api/user/admin/{userId}/role
     * 当 role=1 时，请求体中 username 和 password 为必填项
     */
    @PutMapping("/admin/{userId}/role")
    public Result<Void> updateUserRole(
            @PathVariable Long userId,
            @RequestBody SetAdminDTO dto) {
        requireAdmin();
        userService.updateUserRole(userId, dto);
        return Result.success();
    }

    /**
     * 删除用户
     * DELETE /api/user/admin/{userId}
     */
    @DeleteMapping("/admin/{userId}")
    public Result<Void> deleteUser(@PathVariable Long userId) {
        requireAdmin();
        userService.deleteUser(userId);
        return Result.success();
    }

    // ─── 工具方法 ─────────────────────────────────────────────────────────

    private Long requireLogin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private Long requireAdmin() {
        Long userId = requireLogin();
        User user = userService.getById(userId);
        if (user == null || user.getRole() == null || user.getRole() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
        }
        return userId;
    }
}
