package com.studybuddy.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.common.BizException;
import com.studybuddy.user.dto.UpdateNicknameResp;
import com.studybuddy.user.entity.User;
import com.studybuddy.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final InviteCodeGenerator inviteCodeGenerator;

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

    /** 确保用户有邀请码：无则生成唯一码并落库，返回该码。 */
    public String ensureInviteCode(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new com.studybuddy.common.BizException(40100, "未登录");
        }
        if (u.getInviteCode() != null && !u.getInviteCode().isBlank()) {
            return u.getInviteCode();
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = inviteCodeGenerator.generate();
            boolean taken = userMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getInviteCode, code)) > 0;
            if (taken) {
                continue;
            }
            u.setInviteCode(code);
            u.setUpdatedAt(java.time.LocalDateTime.now());
            userMapper.updateById(u);
            return code;
        }
        throw new com.studybuddy.common.BizException(40410, "邀请码生成失败，请重试");
    }

    /** 修改本人昵称:trim 后须 1-20 字符。 */
    public UpdateNicknameResp updateNickname(Long userId, String nickname) {
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > 20) {
            throw new BizException(40000, "昵称需为 1-20 个字符");
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(40100, "未登录");
        }
        u.setNickname(name);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        return new UpdateNicknameResp(name);
    }
}
