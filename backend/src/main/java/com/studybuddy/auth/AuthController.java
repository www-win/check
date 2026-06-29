package com.studybuddy.auth;

import com.studybuddy.auth.dto.LoginReq;
import com.studybuddy.auth.dto.LoginResp;
import com.studybuddy.auth.dto.SendCodeReq;
import com.studybuddy.auth.dto.WxLoginReq;
import com.studybuddy.common.BizException;
import com.studybuddy.common.R;
import com.studybuddy.user.UserService;
import com.studybuddy.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public R<LoginResp> wxLogin(@RequestHeader(value = "X-WX-OPENID", required = false) String wxOpenid,
                               @RequestBody(required = false) WxLoginReq req) {
        // 微信云托管 + callContainer：网关已校验并注入用户 openid 到 X-WX-OPENID 头，直接信任，无需调微信接口。
        // 回退：非云调用场景（如本地/H5）仍用前端传来的 code 换 openid。
        String openid;
        if (wxOpenid != null && !wxOpenid.isBlank()) {
            openid = wxOpenid;
        } else {
            if (req == null || req.getCode() == null || req.getCode().isBlank()) {
                throw new BizException(40003, "缺少登录凭证");
            }
            openid = wxAuthService.getOpenid(req.getCode());
        }
        User u = userService.findOrCreateByOpenid(openid);
        String token = jwtUtil.generate(u.getId());
        return R.ok(new LoginResp(token, u.getId(), u.getNickname()));
    }
}
