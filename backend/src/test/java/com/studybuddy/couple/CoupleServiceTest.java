package com.studybuddy.couple;

import com.studybuddy.checkin.CheckinService;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.couple.dto.CoupleStatusResp;
import com.studybuddy.couple.entity.Couple;
import com.studybuddy.couple.mapper.CoupleMapper;
import com.studybuddy.couple.mapper.CouplePokeMapper;
import com.studybuddy.user.UserService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoupleServiceTest {

    @Mock CoupleMapper coupleMapper;
    @Mock CouplePokeMapper pokeMapper;
    @Mock UserMapper userMapper;
    @Mock UserService userService;
    @Mock CheckinService checkinService;
    @Mock CheckinRecordMapper recordMapper;

    @InjectMocks CoupleService service;

    private final Long me = 1L;
    private final Long other = 2L;

    private User userWithCode(Long id, String code) {
        User u = new User();
        u.setId(id);
        u.setInviteCode(code);
        u.setNickname("用户" + id);
        return u;
    }

    private Couple active(Long a, Long b) {
        Couple c = new Couple();
        c.setId(99L);
        c.setRequesterId(a);
        c.setTargetId(b);
        c.setStatus(1);
        return c;
    }

    @Test
    void bindWithUnknownCodeThrows40404() {
        when(userMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.bind(me, "ZZZZZZ"));
        assertEquals(40404, e.getCode());
    }

    @Test
    void bindWithOwnCodeThrows40400() {
        when(userMapper.selectOne(any())).thenReturn(userWithCode(me, "ABC234"));
        BizException e = assertThrows(BizException.class, () -> service.bind(me, "ABC234"));
        assertEquals(40400, e.getCode());
    }

    @Test
    void bindWhenIAlreadyActiveThrows40401() {
        when(userMapper.selectOne(any())).thenReturn(userWithCode(other, "ABC234"));
        // findActive(me) 命中
        when(coupleMapper.selectOne(any())).thenReturn(active(me, other));
        BizException e = assertThrows(BizException.class, () -> service.bind(me, "ABC234"));
        assertEquals(40401, e.getCode());
    }

    @Test
    void bindSuccessInsertsPending() {
        when(userMapper.selectOne(any())).thenReturn(userWithCode(other, "ABC234"));
        // 两次 findActive(me)/findActive(other) 都无；selectCount(已有 pending) 为 0
        when(coupleMapper.selectOne(any())).thenReturn(null);
        when(coupleMapper.selectCount(any())).thenReturn(0L);

        service.bind(me, "ABC234");

        verify(coupleMapper, times(1)).insert(any(Couple.class));
    }

    @Test
    void acceptWithoutPendingThrows40405() {
        // findActive(me) 无；找待确认请求也无
        when(coupleMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.accept(me));
        assertEquals(40405, e.getCode());
    }

    @Test
    void statusNoneWhenNoRelation() {
        when(userService.ensureInviteCode(me)).thenReturn("ABC234");
        when(coupleMapper.selectOne(any())).thenReturn(null);
        CoupleStatusResp resp = service.status(me);
        assertEquals("NONE", resp.getStatus());
        assertEquals("ABC234", resp.getMyInviteCode());
    }

    @Test
    void statusActiveReturnsPartnerAndUnread() {
        when(userService.ensureInviteCode(me)).thenReturn("ABC234");
        // findActive(me) 命中
        when(coupleMapper.selectOne(any())).thenReturn(active(me, other));
        when(userMapper.selectById(other)).thenReturn(userWithCode(other, "QQQ234"));
        when(pokeMapper.selectCount(any())).thenReturn(3L);

        CoupleStatusResp resp = service.status(me);

        assertEquals("ACTIVE", resp.getStatus());
        assertEquals("用户2", resp.getPartner().getNickname());
        assertEquals(3L, resp.getUnreadPokeCount());
        assertEquals(99L, resp.getCoupleId());
    }

    @Test
    void unbindWithoutActiveThrows40406() {
        when(coupleMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.unbind(me));
        assertEquals(40406, e.getCode());
        verify(coupleMapper, never()).deleteById((Long) any());
    }
}
