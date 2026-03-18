package org.example.demo1.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.demo1.dto.AdminChangePasswordDTO;
import org.example.demo1.dto.AdminLoginDTO;
import org.example.demo1.dto.SetAdminDTO;
import org.example.demo1.dto.WxLoginDTO;
import org.example.demo1.entity.User;
import org.example.demo1.vo.UserVO;

public interface UserService extends IService<User> {

    /**
     * 微信小程序登录
     */
    UserVO wxLogin(WxLoginDTO dto);

    /**
     * 根据用户ID获取用户信息
     */
    UserVO getUserInfo(Long userId);

    /**
     * 更新用户信息
     */
    void updateUserInfo(Long userId, String nickname, String avatarUrl);

    // ─── 管理员账号密码登录 ───────────────────────────────────────────────

    /**
     * 管理员账号密码登录
     */
    UserVO adminLogin(AdminLoginDTO dto);

    /**
     * 管理员修改密码
     *
     * @param userId 当前管理员ID
     * @param dto    旧密码 + 新密码
     */
    void adminChangePassword(Long userId, AdminChangePasswordDTO dto);

    // ─── 管理员接口 ───────────────────────────────────────────────────────

    /**
     * 分页查询用户列表
     *
     * @param page     页码（从1开始）
     * @param pageSize 每页条数
     * @param keyword  昵称/手机号模糊搜索（可为空）
     */
    IPage<UserVO> listUsers(int page, int pageSize, String keyword);

    /**
     * 禁用/启用用户
     *
     * @param userId 目标用户ID
     * @param status 0-正常，1-禁用
     */
    void updateUserStatus(Long userId, Integer status);

    /**
     * 设置/取消管理员角色
     * 当 role=1 时，dto 中的 username 和 password 为必填项
     *
     * @param userId 目标用户ID
     * @param dto    角色信息（含账号密码）
     */
    void updateUserRole(Long userId, SetAdminDTO dto);

    /**
     * 删除用户（逻辑删除）
     *
     * @param userId 目标用户ID
     */
    void deleteUser(Long userId);

    /**
     * 获取系统用户总数
     */
    long getUserCount();
}
