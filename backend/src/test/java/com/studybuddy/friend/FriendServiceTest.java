package com.studybuddy.friend;

import com.studybuddy.common.BizException;
import com.studybuddy.friend.dto.FriendListResp;
import com.studybuddy.friend.entity.Friendship;
import com.studybuddy.friend.mapper.FriendshipMapper;
import com.studybuddy.user.UserService;
import com.studybuddy.user.entity.User;
import com.studybuddy.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock FriendshipMapper friendshipMapper;
    @Mock UserMapper userMapper;
    @Mock UserService userService;

    @InjectMocks FriendService service;

    private final Long me = 1L;

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setNickname("u" + id);
        u.setAvatar(null);
        return u;
    }

    // ---- request ----

    @Test
    void requestSendsPending() {
        when(userMapper.selectOne(any())).thenReturn(user(2L));
        when(friendshipMapper.selectCount(any())).thenReturn(0L, 0L); // 无已是好友、无待确认

        service.request(me, "ABC123");

        ArgumentCaptor<Friendship> cap = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipMapper).insert(cap.capture());
        assertEquals(me, cap.getValue().getRequesterId());
        assertEquals(2L, cap.getValue().getAddresseeId());
        assertEquals(0, cap.getValue().getStatus());
    }

    @Test
    void requestInvalidCodeThrows41404() {
        when(userMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.request(me, "NOPE"));
        assertEquals(41404, e.getCode());
    }

    @Test
    void requestSelfThrows41400() {
        when(userMapper.selectOne(any())).thenReturn(user(me)); // 邀请码属于自己
        BizException e = assertThrows(BizException.class, () -> service.request(me, "MINE"));
        assertEquals(41400, e.getCode());
    }

    @Test
    void requestAlreadyFriendThrows41401() {
        when(userMapper.selectOne(any())).thenReturn(user(2L));
        when(friendshipMapper.selectCount(any())).thenReturn(1L); // 已是好友
        BizException e = assertThrows(BizException.class, () -> service.request(me, "ABC123"));
        assertEquals(41401, e.getCode());
        verify(friendshipMapper, never()).insert(ArgumentMatchers.<Friendship>any());
    }

    @Test
    void requestDuplicatePendingThrows41403() {
        when(userMapper.selectOne(any())).thenReturn(user(2L));
        when(friendshipMapper.selectCount(any())).thenReturn(0L, 1L); // 无好友、有待确认
        BizException e = assertThrows(BizException.class, () -> service.request(me, "ABC123"));
        assertEquals(41403, e.getCode());
        verify(friendshipMapper, never()).insert(ArgumentMatchers.<Friendship>any());
    }

    // ---- accept / reject / cancel ----

    @Test
    void acceptMarksFriend() {
        Friendship f = new Friendship();
        f.setId(10L);
        f.setRequesterId(3L);
        f.setAddresseeId(me);
        f.setStatus(0);
        when(friendshipMapper.selectById(10L)).thenReturn(f);

        service.accept(me, 10L);

        ArgumentCaptor<Friendship> cap = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipMapper).updateById(cap.capture());
        assertEquals(1, cap.getValue().getStatus());
    }

    @Test
    void acceptByNonAddresseeThrows41405() {
        Friendship f = new Friendship();
        f.setId(10L);
        f.setRequesterId(3L);
        f.setAddresseeId(99L); // 不是 me
        f.setStatus(0);
        when(friendshipMapper.selectById(10L)).thenReturn(f);

        BizException e = assertThrows(BizException.class, () -> service.accept(me, 10L));
        assertEquals(41405, e.getCode());
        verify(friendshipMapper, never()).updateById(ArgumentMatchers.<Friendship>any());
    }

    @Test
    void rejectByAddresseeDeletes() {
        Friendship f = new Friendship();
        f.setId(10L);
        f.setRequesterId(3L);
        f.setAddresseeId(me);
        f.setStatus(0);
        when(friendshipMapper.selectById(10L)).thenReturn(f);

        service.reject(me, 10L);

        verify(friendshipMapper).deleteById(10L);
    }

    @Test
    void cancelByNonRequesterThrows41405() {
        Friendship f = new Friendship();
        f.setId(11L);
        f.setRequesterId(99L); // 不是 me
        f.setAddresseeId(2L);
        f.setStatus(0);
        when(friendshipMapper.selectById(11L)).thenReturn(f);

        BizException e = assertThrows(BizException.class, () -> service.cancel(me, 11L));
        assertEquals(41405, e.getCode());
    }

    // ---- removeFriend ----

    @Test
    void removeFriendDeletes() {
        when(friendshipMapper.delete(any())).thenReturn(1);
        service.removeFriend(me, 2L);
        verify(friendshipMapper).delete(any());
    }

    @Test
    void removeNonFriendThrows41405() {
        when(friendshipMapper.delete(any())).thenReturn(0);
        BizException e = assertThrows(BizException.class, () -> service.removeFriend(me, 2L));
        assertEquals(41405, e.getCode());
    }

    // ---- list ----

    @Test
    void listClassifiesFriendsAndRequests() {
        when(userService.ensureInviteCode(me)).thenReturn("ABC123");

        Friendship accepted = new Friendship();
        accepted.setId(1L);
        accepted.setRequesterId(me);
        accepted.setAddresseeId(2L);
        accepted.setStatus(1);

        Friendship incoming = new Friendship();
        incoming.setId(10L);
        incoming.setRequesterId(3L);
        incoming.setAddresseeId(me);
        incoming.setStatus(0);

        Friendship outgoing = new Friendship();
        outgoing.setId(11L);
        outgoing.setRequesterId(me);
        outgoing.setAddresseeId(4L);
        outgoing.setStatus(0);

        // selectList 调用顺序: accepted, incoming, outgoing
        when(friendshipMapper.selectList(any()))
                .thenReturn(List.of(accepted), List.of(incoming), List.of(outgoing));
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(userMapper.selectById(3L)).thenReturn(user(3L));
        when(userMapper.selectById(4L)).thenReturn(user(4L));

        FriendListResp resp = service.list(me);

        assertEquals("ABC123", resp.getMyInviteCode());
        assertEquals(1, resp.getFriends().size());
        assertEquals(2L, resp.getFriends().get(0).getUserId());
        assertEquals(1, resp.getIncoming().size());
        assertEquals(10L, resp.getIncoming().get(0).getRequestId());
        assertEquals(3L, resp.getIncoming().get(0).getUserId());
        assertEquals(1, resp.getOutgoing().size());
        assertEquals(11L, resp.getOutgoing().get(0).getRequestId());
        assertEquals(4L, resp.getOutgoing().get(0).getUserId());
    }
}
