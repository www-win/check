package com.studybuddy.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/** 短信验证码：Caffeine 缓存 phone -> code，5 分钟有效。开发环境仅日志打印验证码。 */
@Service
public class SmsCodeService {
    private static final Logger log = LoggerFactory.getLogger(SmsCodeService.class);
    private final SecureRandom random = new SecureRandom();
    private final Cache<String, String> cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(100_000)
            .build();

    public void send(String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        cache.put(phone, code);
        // TODO 接入真实短信网关；目前仅开发日志输出
        log.info("[SMS] phone={} code={}", phone, code);
    }

    public boolean verify(String phone, String code) {
        if (phone == null || code == null) {
            return false;
        }
        String cached = cache.getIfPresent(phone);
        if (cached != null && cached.equals(code)) {
            cache.invalidate(phone);
            return true;
        }
        return false;
    }
}
