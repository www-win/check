package com.studybuddy.chat;

import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.entity.ChatMessage;
import com.studybuddy.chat.mapper.ChatMessageMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.friend.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageMapper chatMessageMapper;
    private final FriendService friendService;

    private static final int MAX_CONTENT = 1000;

    @Transactional
    public ChatMessageInfo send(Long me, Long peerId, String content) {
        if (peerId == null || peerId.equals(me)) {
            throw new BizException(41412, "不能给自己发消息");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > MAX_CONTENT) {
            throw new BizException(41411, "消息内容为空或过长");
        }
        if (!friendService.areFriends(me, peerId)) {
            throw new BizException(41410, "对方不是你的好友");
        }
        ChatMessage m = new ChatMessage();
        m.setSenderId(me);
        m.setReceiverId(peerId);
        m.setContent(text);
        m.setIsRead(0);
        m.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(m);
        return new ChatMessageInfo(m.getId(), true, m.getContent(), m.getCreatedAt());
    }
}
