package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
import cn.hutool.crypto.digest.BCrypt;
import org.example.demo1.dto.AdminChangePasswordDTO;
import org.example.demo1.dto.AdminLoginDTO;
import org.example.demo1.dto.SetAdminDTO;
import org.example.demo1.dto.WxLoginDTO;
import org.example.demo1.entity.User;
import org.example.demo1.mapper.UserMapper;
import org.example.demo1.service.UserService;
import org.example.demo1.util.JwtUtil;
import org.example.demo1.util.WechatUtil;
import org.example.demo1.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final WechatUtil wechatUtil;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public UserVO wxLogin(WxLoginDTO dto) {
        // 调用微信接口获取 openid
        WechatUtil.WxSession session = wechatUtil.code2Session(dto.getCode());
        String openid = session.getOpenid();

        // 查找或创建用户
        User user = getOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUnionid(session.getUnionid());
            user.setNickname(dto.getNickname() != null ? dto.getNickname() : "用户" + openid.substring(openid.length() - 6));
            user.setAvatarUrl(dto.getAvatarUrl());
            user.setRole(0);
            user.setStatus(0);
            save(user);
            log.info("新用户注册: userId={}, openid={}", user.getId(), openid);
        } else {
            // 更新昵称和头像
            if (dto.getNickname() != null) {
                user.setNickname(dto.getNickname());
            }
            if (dto.getAvatarUrl() != null) {
                user.setAvatarUrl(dto.getAvatarUrl());
            }
            updateById(user);
        }

        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        String token = jwtUtil.generateToken(user.getId(), openid, user.getRole());

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setToken(token);
        return vo;
    }

    @Override
    public UserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        return vo;
    }

    @Override
    public void updateUserInfo(Long userId, String nickname, String avatarUrl) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (nickname != null) user.setNickname(nickname);
        if (avatarUrl != null) user.setAvatarUrl(avatarUrl);
        updateById(user);
    }

    // ─── 管理员账号密码登录 ───────────────────────────────────────────────

    @Override
    public UserVO adminLogin(AdminLoginDTO dto) {
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "账号和密码不能为空");
        }
        User user = getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername()));
        if (user == null || user.getPassword() == null
                || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.USERNAME_OR_PASSWORD_ERROR);
        }
        if (user.getRole() == null || user.getRole() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "该账号无管理员权限");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        log.info("管理员登录成功: userId={}, username={}", user.getId(), user.getUsername());

        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setToken(token);
        return vo;
    }

    @Override
    public void adminChangePassword(Long userId, AdminChangePasswordDTO dto) {
        if (dto.getOldPassword() == null || dto.getOldPassword().isBlank()
                || dto.getNewPassword() == null || dto.getNewPassword().isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "旧密码和新密码不能为空");
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (user.getPassword() == null || !BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.OLD_PASSWORD_ERROR);
        }
        user.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        updateById(user);
        log.info("管理员修改密码成功: userId={}", userId);
    }

    // ─── 管理员接口实现 ───────────────────────────────────────────────────

    @Override
    public IPage<UserVO> listUsers(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreatedAt);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(User::getNickname, keyword)
                    .or()
                    .like(User::getPhone, keyword)
            );
        }
        IPage<User> userPage = page(new Page<>(page, pageSize), wrapper);
        return userPage.convert(this::toVO);
    }

    @Override
    public void updateUserStatus(Long userId, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "status 只能为 0（正常）或 1（禁用）");
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        user.setStatus(status);
        updateById(user);
        log.info("管理员修改用户状态: userId={}, status={}", userId, status);
    }

    @Override
    public void updateUserRole(Long userId, SetAdminDTO dto) {
        Integer role = dto.getRole();
        if (role == null || (role != 0 && role != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "role 只能为 0（普通用户）或 1（管理员）");
        }
        if (role == 1) {
            if (dto.getUsername() == null || dto.getUsername().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "设置管理员时账号不能为空");
            }
            if (dto.getPassword() == null || dto.getPassword().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "设置管理员时密码不能为空");
            }
            boolean exists = lambdaQuery()
                    .eq(User::getUsername, dto.getUsername())
                    .ne(User::getId, userId)
                    .exists();
            if (exists) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "该账号已被其他用户使用");
            }
        }
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        user.setRole(role);
        if (role == 1) {
            user.setUsername(dto.getUsername());
            user.setPassword(BCrypt.hashpw(dto.getPassword()));
        }
        updateById(user);
        log.info("管理员修改用户角色: userId={}, role={}", userId, role);
    }

    @Override
    public void deleteUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        removeById(userId);
        log.info("管理员删除用户: userId={}", userId);
    }

    @Override
    public long getUserCount() {
        return count();
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhone(user.getPhone());
        vo.setUsername(user.getUsername());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
