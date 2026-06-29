package com.studybuddy.auth;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studybuddy.common.BizException;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** 调用微信 jscode2session，用 code 换 openid。 */
@Service
public class WxAuthService {
    private static final String JSCODE2SESSION =
            "https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    private final String appid;
    private final String secret;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public WxAuthService(@Value("${studybuddy.wx.appid}") String appid,
                         @Value("${studybuddy.wx.secret}") String secret) {
        this.appid = appid;
        this.secret = secret;
        this.restClient = RestClient.create();
    }

    public String getOpenid(String code) {
        if (code == null || code.isBlank()) {
            throw new BizException(40003, "缺少 code");
        }
        // 微信该接口返回 Content-Type 为 text/plain，先取字符串再手动解析，避免转换器不匹配
        String json = restClient.get()
                .uri(JSCODE2SESSION, appid, secret, code)
                .retrieve()
                .body(String.class);

        WxSession session;
        try {
            session = objectMapper.readValue(json, WxSession.class);
        } catch (Exception e) {
            throw new BizException(40003, "微信登录失败");
        }
        if (session.getOpenid() == null || session.getOpenid().isBlank()) {
            String detail = session.getErrmsg() != null ? "：" + session.getErrmsg() : "";
            throw new BizException(40003, "微信登录失败" + detail);
        }
        return session.getOpenid();
    }

    @Data
    static class WxSession {
        private String openid;
        private String session_key;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }
}
