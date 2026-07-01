package com.studybuddy.chat;

import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.entity.ChatMessage;
import com.studybuddy.chat.mapper.ChatMessageMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.friend.FriendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.context.annotation.Import;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatMessageMapper chatMessageMapper;
    @Mock FriendService friendService;

    @InjectMocks ChatService service;

    private final Long me = 1L;
    private final Long peer = 2L;

    // ---- send ----

    @Test
    void sendInsertsAndReturnsMine() {
        when(friendService.areFriends(me, peer)).thenReturn(true);
        doAnswer(inv -> {
            inv.getArgument(0, ChatMessage.class).setId(123L);
            return 1;
        }).when(chatMessageMapper).insert(any(ChatMessage.class));

        ChatMessageInfo info = service.send(me, peer, "  hi  ");

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(cap.capture());
        assertEquals(me, cap.getValue().getSenderId());
        assertEquals(peer, cap.getValue().getReceiverId());
        assertEquals("hi", cap.getValue().getContent()); // trim 生效
        assertEquals(0, cap.getValue().getIsRead());
        assertTrue(info.isMine());
        assertEquals("hi", info.getContent());
        assertEquals(123L, info.getId());
    }

    @Test
    void sendToSelfThrows41412() {
        BizException e = assertThrows(BizException.class, () -> service.send(me, me, "hi"));
        assertEquals(41412, e.getCode());
        verify(chatMessageMapper, never()).insert(ArgumentMatchers.<ChatMessage>any());
    }

    @Test
    void sendEmptyThrows41411() {
        BizException e = assertThrows(BizException.class, () -> service.send(me, peer, "   "));
        assertEquals(41411, e.getCode());
        verify(chatMessageMapper, never()).insert(ArgumentMatchers.<ChatMessage>any());
    }

    @Test
    void sendTooLongThrows41411() {
        String big = "x".repeat(1001);
        BizException e = assertThrows(BizException.class, () -> service.send(me, peer, big));
        assertEquals(41411, e.getCode());
        verify(chatMessageMapper, never()).insert(ArgumentMatchers.<ChatMessage>any());
    }

    @Test
    void sendToNonFriendThrows41410() {
        when(friendService.areFriends(me, peer)).thenReturn(false);
        BizException e = assertThrows(BizException.class, () -> service.send(me, peer, "hi"));
        assertEquals(41410, e.getCode());
        verify(chatMessageMapper, never()).insert(ArgumentMatchers.<ChatMessage>any());
    }

    // ---- messages ----

    @Test
    void messagesIncrementalReturnsAscendingAndMineFlag() {
        when(friendService.areFriends(me, peer)).thenReturn(true);
        ChatMessage a = msg(5L, me, peer, "a");
        ChatMessage b = msg(6L, peer, me, "b");
        when(chatMessageMapper.selectList(any())).thenReturn(List.of(a, b));

        List<ChatMessageInfo> out = service.messages(me, peer, 4L, null);

        assertEquals(2, out.size());
        assertEquals(5L, out.get(0).getId());
        assertTrue(out.get(0).isMine());   // sender=me
        assertFalse(out.get(1).isMine());  // sender=peer
    }

    @Test
    void messagesFirstScreenReversesDescRows() {
        when(friendService.areFriends(me, peer)).thenReturn(true);
        // mapper 按 id desc 返回,service 应 reverse 成升序
        ChatMessage newer = msg(9L, me, peer, "new");
        ChatMessage older = msg(8L, peer, me, "old");
        when(chatMessageMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(newer, older)));

        List<ChatMessageInfo> out = service.messages(me, peer, null, null);

        assertEquals(8L, out.get(0).getId()); // 升序:先 old
        assertEquals(9L, out.get(1).getId());
    }

    @Test
    void messagesNonFriendThrows41410() {
        when(friendService.areFriends(me, peer)).thenReturn(false);
        BizException e = assertThrows(BizException.class, () -> service.messages(me, peer, null, null));
        assertEquals(41410, e.getCode());
    }

    // ---- markRead ----

    @Test
    void markReadUpdatesWithReceiverMeSenderPeer() {
        service.markRead(me, peer);
        verify(chatMessageMapper).update(ArgumentMatchers.isNull(), any());
    }

    private ChatMessage msg(Long id, Long sender, Long receiver, String content) {
        ChatMessage m = new ChatMessage();
        m.setId(id); m.setSenderId(sender); m.setReceiverId(receiver);
        m.setContent(content); m.setIsRead(0);
        return m;
    }
}
