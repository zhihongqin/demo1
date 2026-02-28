package org.example.demo1.service;

import com.baomidou.mybatisplus.extension.service.IService;
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
}
