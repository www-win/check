package com.studybuddy.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.entity.ChatMessage;
import com.studybuddy.chat.mapper.ChatMessageMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.friend.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageMapper chatMessageMapper;
    private final FriendService friendService;

    private static final int MAX_CONTENT = 1000;
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

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

    @Transactional(readOnly = true)
    public List<ChatMessageInfo> messages(Long me, Long peerId, Long afterId, Integer limit) {
        if (!friendService.areFriends(me, peerId)) {
            throw new BizException(41410, "对方不是你的好友");
        }
        List<ChatMessage> rows;
        if (afterId != null && afterId > 0) {
            rows = chatMessageMapper.selectList(pairWrapper(me, peerId)
                    .gt(ChatMessage::getId, afterId)
                    .orderByAsc(ChatMessage::getId));
        } else {
            int n = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
            rows = chatMessageMapper.selectList(pairWrapper(me, peerId)
                    .orderByDesc(ChatMessage::getId)
                    .last("LIMIT " + n));
            Collections.reverse(rows);
        }
        List<ChatMessageInfo> out = new ArrayList<>();
        for (ChatMessage m : rows) {
            out.add(new ChatMessageInfo(m.getId(), m.getSenderId().equals(me), m.getContent(), m.getCreatedAt()));
        }
        return out;
    }

    /** (sender=me,receiver=peer) OR (sender=peer,receiver=me) 的双向会话条件。 */
    private LambdaQueryWrapper<ChatMessage> pairWrapper(Long me, Long peerId) {
        return new LambdaQueryWrapper<ChatMessage>()
                .and(w -> w.nested(n -> n.eq(ChatMessage::getSenderId, me).eq(ChatMessage::getReceiverId, peerId))
                        .or().nested(n -> n.eq(ChatMessage::getSenderId, peerId).eq(ChatMessage::getReceiverId, me)));
    }
}
