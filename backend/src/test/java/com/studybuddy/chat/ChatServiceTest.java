package com.studybuddy.chat;

import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.entity.ChatMessage;
import com.studybuddy.chat.mapper.ChatMessageMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.friend.FriendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        ChatMessageInfo info = service.send(me, peer, "  hi  ");

        ArgumentCaptor<ChatMessage> cap = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageMapper).insert(cap.capture());
        assertEquals(me, cap.getValue().getSenderId());
        assertEquals(peer, cap.getValue().getReceiverId());
        assertEquals("hi", cap.getValue().getContent()); // trim 生效
        assertEquals(0, cap.getValue().getIsRead());
        assertTrue(info.isMine());
        assertEquals("hi", info.getContent());
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
}
