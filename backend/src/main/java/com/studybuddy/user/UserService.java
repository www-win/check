package com.studybuddy.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.user.entity.User;
import com.studybuddy.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    /** 手机号登录：不存在则建号（隐式注册）。 */
    public User findOrCreateByPhone(String phone) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (u == null) {
            u = new User();
            u.setPhone(phone);
            u.setNickname("用户" + phone.substring(Math.max(0, phone.length() - 4)));
            LocalDateTime now = LocalDateTime.now();
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            userMapper.insert(u);
        }
        return u;
    }

    /** 微信 openid 登录：不存在则建号（隐式注册）。 */
    public User findOrCreateByOpenid(String openid) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (u == null) {
            u = new User();
            u.setOpenid(openid);
            u.setNickname("微信用户");
            LocalDateTime now = LocalDateTime.now();
            u.setCreatedAt(now);
            u.setUpdatedAt(now);
            userMapper.insert(u);
        }
        return u;
    }
}
