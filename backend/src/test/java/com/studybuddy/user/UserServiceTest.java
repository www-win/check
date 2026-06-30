package com.studybuddy.user;

import com.studybuddy.common.BizException;
import com.studybuddy.user.dto.UpdateNicknameResp;
import com.studybuddy.user.entity.User;
import com.studybuddy.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Note: MyBatis-Plus 3.5.7 added updateById(Collection<T>) overload.
// Cast to (User) is used to disambiguate the single-entity overload in verify calls.

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock InviteCodeGenerator inviteCodeGenerator;

    @InjectMocks UserService service;

    private final Long uid = 1L;

    @Test
    void updateNicknameTrimsAndSaves() {
        User u = new User();
        u.setId(uid);
        when(userMapper.selectById(uid)).thenReturn(u);

        UpdateNicknameResp resp = service.updateNickname(uid, "  Alice  ");

        assertEquals("Alice", resp.getNickname());
        verify(userMapper).updateById((User) u);
        assertEquals("Alice", u.getNickname());
    }

    @Test
    void updateNicknameBlankThrows40000() {
        BizException e = assertThrows(BizException.class,
                () -> service.updateNickname(uid, "   "));
        assertEquals(40000, e.getCode());
        verify(userMapper, never()).updateById((User) any());
    }

    @Test
    void updateNicknameTooLongThrows40000() {
        String tooLong = "a".repeat(21);
        BizException e = assertThrows(BizException.class,
                () -> service.updateNickname(uid, tooLong));
        assertEquals(40000, e.getCode());
        verify(userMapper, never()).updateById((User) any());
    }

    @Test
    void updateNicknameUserNotFoundThrows40100() {
        when(userMapper.selectById(uid)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.updateNickname(uid, "Bob"));
        assertEquals(40100, e.getCode());
        verify(userMapper, never()).updateById((User) any());
    }
}
