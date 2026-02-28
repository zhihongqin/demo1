package org.example.demo1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.demo1.common.exception.BusinessException;
import org.example.demo1.common.result.ResultCode;
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

        String token = jwtUtil.generateToken(user.getId(), openid);

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
}
