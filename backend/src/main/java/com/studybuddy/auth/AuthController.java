package com.studybuddy.auth;

import com.studybuddy.auth.dto.LoginReq;
import com.studybuddy.auth.dto.LoginResp;
import com.studybuddy.auth.dto.SendCodeReq;
import com.studybuddy.auth.dto.WxLoginReq;
import com.studybuddy.common.BizException;
import com.studybuddy.common.R;
import com.studybuddy.user.UserService;
import com.studybuddy.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final SmsCodeService smsCodeService;
    private final WxAuthService wxAuthService;
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/send-code")
    public R<Void> sendCode(@RequestBody SendCodeReq req) {
        if (req.getPhone() == null || !req.getPhone().matches("\\d{11}")) {
            throw new BizException(40001, "手机号格式不正确");
        }
        smsCodeService.send(req.getPhone());
        return R.ok();
    }

    @PostMapping("/login")
    public R<LoginResp> login(@RequestBody LoginReq req) {
        if (!smsCodeService.verify(req.getPhone(), req.getCode())) {
            throw new BizException(40002, "验证码错误或已过期");
        }
        User u = userService.findOrCreateByPhone(req.getPhone());
        String token = jwtUtil.generate(u.getId());
        return R.ok(new LoginResp(token, u.getId(), u.getNickname()));
    }

    @PostMapping("/wx-login")
    public R<LoginResp> wxLogin(@Valid @RequestBody WxLoginReq req) {
        String openid = wxAuthService.getOpenid(req.getCode());
        User u = userService.findOrCreateByOpenid(openid);
        String token = jwtUtil.generate(u.getId());
        return R.ok(new LoginResp(token, u.getId(), u.getNickname()));
    }
}
