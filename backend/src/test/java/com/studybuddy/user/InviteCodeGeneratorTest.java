package com.studybuddy.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteCodeGeneratorTest {
    private final InviteCodeGenerator gen = new InviteCodeGenerator();

    @Test
    void generatesSixCharsFromAllowedAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = gen.generate();
            assertEquals(6, code.length(), "应为 6 位");
            assertTrue(code.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"),
                    "只能含去易混字符的大写字母与数字: " + code);
        }
    }
}
