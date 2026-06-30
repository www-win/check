package com.studybuddy.user;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 生成 6 位邀请码，去除易混字符（0/O/1/I）。 */
@Component
public class InviteCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
