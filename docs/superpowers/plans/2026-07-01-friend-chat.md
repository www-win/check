# 好友私聊(文字聊天) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让互为好友(friendship status=1)的两人进行一对一纯文本私聊,聊天页轮询收新消息,好友页显示未读角标与末条预览。

**Architecture:** 后端新增独立模块 `com.studybuddy.chat`(单表 `chat_message` + is_read 标记,方案 1),复用 `FriendService` 做好友校验与好友列表;小程序新增 `pages/chat` 聊天页,`pages/friends` 改造为会话列表。云托管无长连接,全部走轮询(聊天页 3s、好友页 10s)。

**Tech Stack:** Spring Boot + MyBatis-Plus(BaseMapper + LambdaQueryWrapper)、JUnit5 + Mockito 单测;uni-app(Vue3 setup)小程序,`wx.cloud.callContainer` 请求。

## Global Constraints

- 统一响应体 `R<T>`(code=0 成功);业务错误抛 `BizException(code, msg)`,由 `GlobalExceptionHandler` 转 `R`。
- 登录态用 `CurrentUser.get()`(ThreadLocal,未登录抛 40100)。
- 所有 chat 接口:要求登录 + `peerId` 必须是当前用户的 status=1 好友,否则 41410。
- 错误码段位:chat 用 41410 起(41410 非好友 / 41411 内容为空或过长 / 41412 不能给自己发)。
- 消息 content:后端 `trim()` 后判非空,长度 ≤ 1000。
- `created_at` 由后端 `LocalDateTime.now()` 落库;`ChatMessageInfo.mine` 由后端相对 `CurrentUser` 计算(前端不需要知道自己的 userId)。
- schema.sql 只放逐句、幂等、无 DELIMITER 的语句(`CREATE TABLE IF NOT EXISTS`)。
- 轮询间隔写成常量:聊天页 `POLL_MS=3000`、好友页 `CONV_POLL_MS=10000`。
- 单测风格照 `FriendServiceTest`:`@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`,mock mapper 返回值。

---

### Task 1: FriendService 复用方法(areFriends + acceptedFriends)

ChatService 需要「判断两人是否好友」与「取我的好友列表」。在 `FriendService` 暴露两个 public 方法并让 `list()` 复用后者。

**Files:**
- Modify: `backend/src/main/java/com/studybuddy/friend/FriendService.java`
- Test: `backend/src/test/java/com/studybuddy/friend/FriendServiceTest.java`

**Interfaces:**
- Produces:
  - `boolean FriendService.areFriends(Long a, Long b)` — a、b 为 status=1 好友返回 true;任一为 null 或相等返回 false。
  - `List<FriendInfo> FriendService.acceptedFriends(Long me)` — 我的所有 status=1 好友(FriendInfo: userId/nickname/avatar)。

- [ ] **Step 1: 写失败测试**(追加到 `FriendServiceTest`)

```java
    // ---- areFriends / acceptedFriends ----

    @Test
    void areFriendsTrueWhenAcceptedRowExists() {
        when(friendshipMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.areFriends(me, 2L));
    }

    @Test
    void areFriendsFalseWhenNoRow() {
        when(friendshipMapper.selectCount(any())).thenReturn(0L);
        assertFalse(service.areFriends(me, 2L));
    }

    @Test
    void areFriendsFalseForSelfWithoutQuery() {
        assertFalse(service.areFriends(me, me));
        verify(friendshipMapper, never()).selectCount(any());
    }

    @Test
    void acceptedFriendsMapsOtherSide() {
        Friendship a1 = new Friendship();
        a1.setRequesterId(me); a1.setAddresseeId(2L); a1.setStatus(1);
        Friendship a2 = new Friendship();
        a2.setRequesterId(3L); a2.setAddresseeId(me); a2.setStatus(1);
        when(friendshipMapper.selectList(any())).thenReturn(List.of(a1, a2));
        when(userMapper.selectById(2L)).thenReturn(user(2L));
        when(userMapper.selectById(3L)).thenReturn(user(3L));

        List<FriendInfo> friends = service.acceptedFriends(me);

        assertEquals(2, friends.size());
        assertEquals(2L, friends.get(0).getUserId());
        assertEquals(3L, friends.get(1).getUserId());
    }
```

在文件顶部 import 区补上(若缺):
```java
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.studybuddy.friend.dto.FriendInfo;
```

- [ ] **Step 2: 跑测试确认失败**

Run(仓库根):`mvn -f backend/pom.xml -q -Dtest=FriendServiceTest test`(本机无 mvnw;`mvn` 在便携 Maven,PATH 未生效时先注入 JAVA_HOME/MAVEN_HOME,见 memory `toolchain-portable`)
Expected: 编译失败 / FAIL,提示 `areFriends`、`acceptedFriends` 未定义。

- [ ] **Step 3: 实现**(在 `FriendService` 内新增方法,并改造 `list()` 复用)

新增两个 public 方法(放在 `list()` 之后):
```java
    /** a、b 是否为 status=1 好友。 */
    public boolean areFriends(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        return friendshipMapper.selectCount(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, ACCEPTED)
                .and(w -> w.nested(n -> n.eq(Friendship::getRequesterId, a).eq(Friendship::getAddresseeId, b))
                        .or().nested(n -> n.eq(Friendship::getRequesterId, b).eq(Friendship::getAddresseeId, a)))) > 0;
    }

    /** 我的所有 status=1 好友(含昵称头像)。 */
    public List<FriendInfo> acceptedFriends(Long me) {
        List<Friendship> accepted = friendshipMapper.selectList(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, ACCEPTED)
                .and(w -> w.eq(Friendship::getRequesterId, me).or().eq(Friendship::getAddresseeId, me)));
        List<FriendInfo> friends = new ArrayList<>();
        for (Friendship f : accepted) {
            Long otherId = f.getRequesterId().equals(me) ? f.getAddresseeId() : f.getRequesterId();
            friends.add(toFriendInfo(otherId));
        }
        return friends;
    }
```

把 `list()` 里手动构建 friends 的那段(取 accepted、循环 toFriendInfo)替换为复用:
```java
        // 替换 list() 中:List<Friendship> accepted = ...; for(...) friends.add(toFriendInfo(otherId));
        List<FriendInfo> friends = acceptedFriends(me);
```
(即删掉 `list()` 里原 `accepted` 查询与循环,改成上面一行;其余 incoming/outgoing 逻辑不变。)

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -f backend/pom.xml -q -Dtest=FriendServiceTest test`
Expected: PASS(含原有 list 测试仍通过)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/studybuddy/friend/FriendService.java backend/src/test/java/com/studybuddy/friend/FriendServiceTest.java
git commit -m "feat(chat): FriendService 暴露 areFriends/acceptedFriends 供聊天复用"
```

---

### Task 2: 发消息 send(含实体/mapper/DTO/建表脚手架)

本任务落地 chat 模块骨架(实体、mapper、DTO、建表)并实现 `ChatService.send`。脚手架随首个业务方法一起交付。

**Files:**
- Create: `backend/src/main/java/com/studybuddy/chat/entity/ChatMessage.java`
- Create: `backend/src/main/java/com/studybuddy/chat/mapper/ChatMessageMapper.java`
- Create: `backend/src/main/java/com/studybuddy/chat/dto/SendMsgReq.java`
- Create: `backend/src/main/java/com/studybuddy/chat/dto/ChatMessageInfo.java`
- Create: `backend/src/main/java/com/studybuddy/chat/ChatService.java`
- Modify: `backend/src/main/resources/db/schema.sql`(追加建表)
- Test: `backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java`

**Interfaces:**
- Consumes: `FriendService.areFriends(Long, Long)`(Task 1)。
- Produces:
  - `ChatMessage` 实体(id/senderId/receiverId/content/isRead/createdAt)。
  - `ChatMessageMapper extends BaseMapper<ChatMessage>`。
  - `SendMsgReq`(peerId/content)。
  - `ChatMessageInfo(Long id, boolean mine, String content, LocalDateTime createdAt)`。
  - `ChatMessageInfo ChatService.send(Long me, Long peerId, String content)`。

- [ ] **Step 1: 建脚手架文件**(无逻辑,先建好实体/mapper/DTO/建表,供测试编译)

`entity/ChatMessage.java`:
```java
package com.studybuddy.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private Long receiverId;
    private String content;
    private Integer isRead; // 0未读 1已读(receiver 视角)
    private LocalDateTime createdAt;
}
```

`mapper/ChatMessageMapper.java`:
```java
package com.studybuddy.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
```

`dto/SendMsgReq.java`:
```java
package com.studybuddy.chat.dto;

import lombok.Data;

@Data
public class SendMsgReq {
    private Long peerId;
    private String content;
}
```

`dto/ChatMessageInfo.java`:
```java
package com.studybuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatMessageInfo {
    private Long id;
    private boolean mine;
    private String content;
    private LocalDateTime createdAt;
}
```

在 `backend/src/main/resources/db/schema.sql` 末尾追加:
```sql
-- 私聊消息
CREATE TABLE IF NOT EXISTS chat_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_id   BIGINT        NOT NULL,
  receiver_id BIGINT        NOT NULL,
  content     VARCHAR(1000) NOT NULL,
  is_read     TINYINT       NOT NULL DEFAULT 0,
  created_at  DATETIME      NOT NULL,
  KEY idx_pair_time (sender_id, receiver_id, id),
  KEY idx_unread (receiver_id, is_read)
);
```

建一个占位 `ChatService`(只留 send 方法签名抛异常,让测试能编译并失败):
```java
package com.studybuddy.chat;

import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.mapper.ChatMessageMapper;
import com.studybuddy.friend.FriendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final ChatMessageMapper chatMessageMapper;
    private final FriendService friendService;

    public ChatMessageInfo send(Long me, Long peerId, String content) {
        throw new UnsupportedOperationException();
    }
}
```

- [ ] **Step 2: 写失败测试**(新建 `ChatServiceTest`)

```java
package com.studybuddy.chat;

import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.entity.ChatMessage;
import com.studybuddy.chat.mapper.ChatMessageMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.friend.FriendService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        verify(chatMessageMapper, never()).insert(any());
    }

    @Test
    void sendEmptyThrows41411() {
        BizException e = assertThrows(BizException.class, () -> service.send(me, peer, "   "));
        assertEquals(41411, e.getCode());
        verify(chatMessageMapper, never()).insert(any());
    }

    @Test
    void sendTooLongThrows41411() {
        String big = "x".repeat(1001);
        BizException e = assertThrows(BizException.class, () -> service.send(me, peer, big));
        assertEquals(41411, e.getCode());
        verify(chatMessageMapper, never()).insert(any());
    }

    @Test
    void sendToNonFriendThrows41410() {
        when(friendService.areFriends(me, peer)).thenReturn(false);
        BizException e = assertThrows(BizException.class, () -> service.send(me, peer, "hi"));
        assertEquals(41410, e.getCode());
        verify(chatMessageMapper, never()).insert(any());
    }
}
```

- [ ] **Step 3: 跑测试确认失败**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: FAIL(`UnsupportedOperationException` / 断言失败)。

- [ ] **Step 4: 实现 send**(替换 `ChatService.send`,补 import 与常量)

```java
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
```

- [ ] **Step 5: 跑测试确认通过**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/studybuddy/chat backend/src/main/resources/db/schema.sql backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java
git commit -m "feat(chat): chat_message 数据层 + ChatService.send 发消息"
```

---

### Task 3: 拉消息 messages(首屏 + afterId 增量)

**Files:**
- Modify: `backend/src/main/java/com/studybuddy/chat/ChatService.java`
- Test: `backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java`

**Interfaces:**
- Consumes: `FriendService.areFriends`。
- Produces: `List<ChatMessageInfo> ChatService.messages(Long me, Long peerId, Long afterId, Integer limit)` — `afterId>0` 取该 id 之后新消息(升序);否则取最近 `limit` 条(默认 30、上限 100)并按 id 升序返回。非好友抛 41410。

- [ ] **Step 1: 写失败测试**(追加到 `ChatServiceTest`)

```java
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
        when(chatMessageMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(newer, older)));

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
```

在 `ChatServiceTest` 顶部补 import 与 helper:
```java
import com.studybuddy.chat.dto.ChatMessageInfo;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
```
并在类内加工厂方法:
```java
    private ChatMessage msg(Long id, Long sender, Long receiver, String content) {
        ChatMessage m = new ChatMessage();
        m.setId(id); m.setSenderId(sender); m.setReceiverId(receiver);
        m.setContent(content); m.setIsRead(0);
        return m;
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: FAIL,`messages` 未定义。

- [ ] **Step 3: 实现 messages**(在 `ChatService` 内新增;补 import)

补 import:
```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.chat.entity.ChatMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
```
新增常量与方法:
```java
    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

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
```

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/studybuddy/chat/ChatService.java backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java
git commit -m "feat(chat): ChatService.messages 首屏+afterId 增量拉消息"
```

---

### Task 4: 标记已读 markRead

**Files:**
- Modify: `backend/src/main/java/com/studybuddy/chat/ChatService.java`
- Test: `backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java`

**Interfaces:**
- Produces: `void ChatService.markRead(Long me, Long peerId)` — 把 `receiver=me AND sender=peer AND is_read=0` 的行置 1。

- [ ] **Step 1: 写失败测试**(追加到 `ChatServiceTest`)

```java
    // ---- markRead ----

    @Test
    void markReadUpdatesWithReceiverMeSenderPeer() {
        service.markRead(me, peer);
        verify(chatMessageMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
    }
```

- [ ] **Step 2: 跑测试确认失败**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: FAIL,`markRead` 未定义。

- [ ] **Step 3: 实现 markRead**(补 import + 方法)

补 import:
```java
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
```
新增方法:
```java
    @Transactional
    public void markRead(Long me, Long peerId) {
        chatMessageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getReceiverId, me)
                .eq(ChatMessage::getSenderId, peerId)
                .eq(ChatMessage::getIsRead, 0)
                .set(ChatMessage::getIsRead, 1));
    }
```

- [ ] **Step 4: 跑测试确认通过**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/studybuddy/chat/ChatService.java backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java
git commit -m "feat(chat): ChatService.markRead 标记会话已读"
```

---

### Task 5: 会话列表 conversations

**Files:**
- Create: `backend/src/main/java/com/studybuddy/chat/dto/ConversationInfo.java`
- Modify: `backend/src/main/java/com/studybuddy/chat/ChatService.java`
- Test: `backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java`

**Interfaces:**
- Consumes: `FriendService.acceptedFriends(Long)`(Task 1)、`FriendInfo`(userId/nickname/avatar)。
- Produces:
  - `ConversationInfo(Long peerUserId, String nickname, String avatar, String lastContent, LocalDateTime lastTime, long unread)`。
  - `List<ConversationInfo> ChatService.conversations(Long me)` — 仅含「有过消息」的好友;按 lastTime 倒序;无消息的好友跳过。

- [ ] **Step 1: 建 DTO**

`dto/ConversationInfo.java`:
```java
package com.studybuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ConversationInfo {
    private Long peerUserId;
    private String nickname;
    private String avatar;
    private String lastContent;
    private LocalDateTime lastTime;
    private long unread;
}
```

- [ ] **Step 2: 写失败测试**(追加到 `ChatServiceTest`)

```java
    // ---- conversations ----

    @Test
    void conversationsSkipsFriendsWithoutMessages() {
        FriendInfo f2 = new FriendInfo(peer, "u2", null);
        FriendInfo f3 = new FriendInfo(3L, "u3", null);
        when(friendService.acceptedFriends(me)).thenReturn(List.of(f2, f3));
        // peer 有末条消息,3L 没有
        ChatMessage last = msg(7L, peer, me, "yo");
        last.setCreatedAt(java.time.LocalDateTime.of(2026, 7, 1, 10, 0));
        when(chatMessageMapper.selectOne(any())).thenReturn(last, (ChatMessage) null);
        when(chatMessageMapper.selectCount(any())).thenReturn(2L);

        List<ConversationInfo> out = service.conversations(me);

        assertEquals(1, out.size());
        assertEquals(peer, out.get(0).getPeerUserId());
        assertEquals("yo", out.get(0).getLastContent());
        assertEquals(2L, out.get(0).getUnread());
    }
```

顶部补 import:
```java
import com.studybuddy.chat.dto.ConversationInfo;
import com.studybuddy.friend.dto.FriendInfo;
```

- [ ] **Step 3: 跑测试确认失败**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: FAIL,`conversations` 未定义。

- [ ] **Step 4: 实现 conversations**(补 import + 方法)

补 import:
```java
import com.studybuddy.chat.dto.ConversationInfo;
import com.studybuddy.friend.dto.FriendInfo;
```
新增方法:
```java
    @Transactional(readOnly = true)
    public List<ConversationInfo> conversations(Long me) {
        List<ConversationInfo> out = new ArrayList<>();
        for (FriendInfo f : friendService.acceptedFriends(me)) {
            Long peer = f.getUserId();
            ChatMessage last = chatMessageMapper.selectOne(pairWrapper(me, peer)
                    .orderByDesc(ChatMessage::getId)
                    .last("LIMIT 1"));
            if (last == null) {
                continue;
            }
            long unread = chatMessageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                    .eq(ChatMessage::getReceiverId, me)
                    .eq(ChatMessage::getSenderId, peer)
                    .eq(ChatMessage::getIsRead, 0));
            out.add(new ConversationInfo(peer, f.getNickname(), f.getAvatar(),
                    last.getContent(), last.getCreatedAt(), unread));
        }
        out.sort((a, b) -> b.getLastTime().compareTo(a.getLastTime()));
        return out;
    }
```

- [ ] **Step 5: 跑测试确认通过**

Run: `mvn -f backend/pom.xml -q -Dtest=ChatServiceTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/studybuddy/chat/dto/ConversationInfo.java backend/src/main/java/com/studybuddy/chat/ChatService.java backend/src/test/java/com/studybuddy/chat/ChatServiceTest.java
git commit -m "feat(chat): ChatService.conversations 会话列表(末条+未读数)"
```

---

### Task 6: ChatController(REST 接口)+ 全量测试

把四个 service 方法暴露为 `/api/chat` 接口。本模块 controller 无单测惯例(照 FriendController),交付验证靠编译 + 全量测试通过。

**Files:**
- Create: `backend/src/main/java/com/studybuddy/chat/dto/ReadReq.java`
- Create: `backend/src/main/java/com/studybuddy/chat/ChatController.java`

**Interfaces:**
- Consumes: `ChatService` 全部方法、`CurrentUser.get()`、`R`。
- Produces(前端 Task 7/8 依赖的接口):
  - `GET /api/chat/conversations` → `R<List<ConversationInfo>>`
  - `GET /api/chat/messages?peerId=&afterId=&limit=` → `R<List<ChatMessageInfo>>`
  - `POST /api/chat/messages` body `SendMsgReq{peerId,content}` → `R<ChatMessageInfo>`
  - `POST /api/chat/read` body `ReadReq{peerId}` → `R<Void>`

- [ ] **Step 1: 建 ReadReq DTO**

`dto/ReadReq.java`:
```java
package com.studybuddy.chat.dto;

import lombok.Data;

@Data
public class ReadReq {
    private Long peerId;
}
```

- [ ] **Step 2: 建 ChatController**

`ChatController.java`:
```java
package com.studybuddy.chat;

import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.dto.ConversationInfo;
import com.studybuddy.chat.dto.ReadReq;
import com.studybuddy.chat.dto.SendMsgReq;
import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @GetMapping("/conversations")
    public R<List<ConversationInfo>> conversations() {
        return R.ok(chatService.conversations(CurrentUser.get()));
    }

    @GetMapping("/messages")
    public R<List<ChatMessageInfo>> messages(@RequestParam Long peerId,
                                             @RequestParam(required = false) Long afterId,
                                             @RequestParam(required = false) Integer limit) {
        return R.ok(chatService.messages(CurrentUser.get(), peerId, afterId, limit));
    }

    @PostMapping("/messages")
    public R<ChatMessageInfo> send(@RequestBody SendMsgReq req) {
        return R.ok(chatService.send(CurrentUser.get(), req.getPeerId(), req.getContent()));
    }

    @PostMapping("/read")
    public R<Void> read(@RequestBody ReadReq req) {
        chatService.markRead(CurrentUser.get(), req.getPeerId());
        return R.ok();
    }
}
```

- [ ] **Step 3: 全量编译 + 测试**

Run: `mvn -f backend/pom.xml -q test`
Expected: BUILD SUCCESS,全部测试(含 FriendServiceTest、ChatServiceTest)PASS。

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/studybuddy/chat/ChatController.java backend/src/main/java/com/studybuddy/chat/dto/ReadReq.java
git commit -m "feat(chat): ChatController 暴露 /api/chat 接口"
```

---

### Task 7: 小程序 API 封装 + 聊天页 chat.vue

**Files:**
- Modify: `miniprogram/src/utils/request.js`(追加 chat API)
- Modify: `miniprogram/src/pages.json`(注册 chat 页)
- Create: `miniprogram/src/pages/chat/chat.vue`

**Interfaces:**
- Consumes: 后端 `/api/chat/*`(Task 6)。
- Produces(供 friends.vue 跳转):路由 `pages/chat/chat?peerId=<id>&name=<encodeURIComponent(昵称)>`。

- [ ] **Step 1: 追加 chat API**(`request.js` 末尾,情侣封装之后)

```js
// ===== 聊天 =====
export const getConversations = () => request('/chat/conversations')
export const getMessages = (peerId, afterId) =>
  request('/chat/messages?peerId=' + peerId + (afterId ? '&afterId=' + afterId : ''))
export const sendMessage = (peerId, content) => request('/chat/messages', { method: 'POST', data: { peerId, content } })
export const markChatRead = (peerId) => request('/chat/read', { method: 'POST', data: { peerId } })
```

- [ ] **Step 2: 注册 chat 页**(`pages.json` 的 `pages` 数组末尾追加,注意前一项补逗号)

```json
    ,{
      "path": "pages/chat/chat",
      "style": {
        "navigationBarTitleText": "聊天"
      }
    }
```

- [ ] **Step 3: 建聊天页 `pages/chat/chat.vue`**

```vue
<script setup>
import { ref, nextTick } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getMessages, sendMessage, markChatRead, toast } from '../../utils/request'

const POLL_MS = 3000
const peerId = ref(null)
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const scrollTop = ref(0)
let timer = null

onLoad((q) => {
  peerId.value = Number(q.peerId)
  uni.setNavigationBarTitle({ title: q.name ? decodeURIComponent(q.name) : '聊天' })
  firstLoad()
  timer = setInterval(poll, POLL_MS)
})

onUnload(() => { if (timer) clearInterval(timer) })

function maxId() {
  return messages.value.length ? messages.value[messages.value.length - 1].id : 0
}

function scrollToBottom() {
  nextTick(() => { scrollTop.value = messages.value.length * 100000 })
}

function firstLoad() {
  getMessages(peerId.value).then((list) => {
    messages.value = list || []
    scrollToBottom()
    if (messages.value.length) markChatRead(peerId.value)
  }).catch((e) => toast(e.message))
}

function poll() {
  getMessages(peerId.value, maxId()).then((list) => {
    if (list && list.length) {
      messages.value = messages.value.concat(list)
      scrollToBottom()
      markChatRead(peerId.value)
    }
  }).catch(() => {})
}

function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  sending.value = true
  sendMessage(peerId.value, text).then((msg) => {
    messages.value.push(msg)
    draft.value = ''
    scrollToBottom()
  }).catch((e) => toast(e.message)).finally(() => { sending.value = false })
}
</script>

<template>
  <view class="chat-page">
    <scroll-view class="msg-list" scroll-y :scroll-top="scrollTop" scroll-with-animation>
      <view v-if="!messages.length" class="empty">打个招呼吧 👋</view>
      <view v-for="m in messages" :key="m.id" class="msg-row" :class="{ mine: m.mine }">
        <view class="bubble">{{ m.content }}</view>
      </view>
    </scroll-view>
    <view class="input-bar">
      <input class="input" v-model="draft" placeholder="说点什么..." confirm-type="send" @confirm="send" />
      <text class="send-btn" :class="{ disabled: !draft.trim() || sending }" @tap="send">发送</text>
    </view>
  </view>
</template>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; background: #F1FAF4; }
.msg-list { flex: 1; padding: 20rpx; box-sizing: border-box; }
.empty { text-align: center; color: var(--c-muted, #8A9A90); font-size: 26rpx; padding: 60rpx 0; }
.msg-row { display: flex; margin-bottom: 20rpx; }
.msg-row.mine { justify-content: flex-end; }
.bubble { max-width: 70%; padding: 18rpx 24rpx; border-radius: 20rpx; font-size: 30rpx;
  background: #fff; color: #222; word-break: break-all; }
.msg-row.mine .bubble { background: #2E9E5B; color: #fff; }
.input-bar { display: flex; align-items: center; gap: 16rpx; padding: 16rpx 20rpx;
  background: #fff; border-top: 1rpx solid #E5EDE8; }
.input { flex: 1; height: 72rpx; padding: 0 24rpx; background: #F0F2F1; border-radius: 36rpx; font-size: 30rpx; }
.send-btn { padding: 0 32rpx; height: 72rpx; line-height: 72rpx; border-radius: 36rpx;
  background: #2E9E5B; color: #fff; font-size: 28rpx; }
.send-btn.disabled { background: #B8C6BE; }
</style>
```

- [ ] **Step 4: 提交**

```bash
git add miniprogram/src/utils/request.js miniprogram/src/pages.json miniprogram/src/pages/chat/chat.vue
git commit -m "feat(mp): 聊天页 chat.vue + chat API 封装"
```

- [ ] **Step 5: 手动验证(与 Task 8 合并做)**

见 Task 8 手动验证。

---

### Task 8: 好友页改造为会话列表(未读角标 + 末条预览 + 进聊天)

**Files:**
- Modify: `miniprogram/src/pages/friends/friends.vue`

**Interfaces:**
- Consumes: `getConversations`(Task 7)、路由 `pages/chat/chat`。

- [ ] **Step 1: 引入 conversations 轮询与跳转**(改 `friends.vue` 的 `<script setup>`)

把顶部 import 与逻辑替换为(在原有基础上新增 `onHide`、`getConversations`、`convMap`、轮询、`openChat`):
```js
import { ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import {
  getFriends, addFriend, acceptFriend, rejectFriend, cancelFriend, removeFriend,
  getConversations, toast
} from '../../utils/request'

const CONV_POLL_MS = 10000
const data = ref(null)
const convMap = ref({}) // peerUserId -> { lastContent, unread }
let convTimer = null

function load() {
  getFriends().then((d) => (data.value = d)).catch((e) => toast(e.message))
}

function loadConversations() {
  getConversations().then((list) => {
    const map = {}
    for (const c of (list || [])) map[c.peerUserId] = c
    convMap.value = map
  }).catch(() => {})
}

onShow(() => {
  load()
  loadConversations()
  convTimer = setInterval(loadConversations, CONV_POLL_MS)
})

onHide(() => { if (convTimer) clearInterval(convTimer) })

function openChat(f) {
  uni.navigateTo({
    url: '/pages/chat/chat?peerId=' + f.userId + '&name=' + encodeURIComponent(f.nickname)
  })
}
```
(保留原有 `copyCode`、`addByCode`、`accept`、`reject`、`cancelReq`、`del` 不变。)

- [ ] **Step 2: 好友列表项改为可点进聊天 + 未读角标/预览**(改 `<template>` 里「好友列表」那段 card)

把好友列表的 card 替换为:
```html
    <view class="card" v-for="f in (data ? data.friends : [])" :key="'f' + f.userId" @tap="openChat(f)">
      <view class="frow">
        <view class="fmain">
          <view class="fname">
            {{ f.nickname }}
            <text v-if="convMap[f.userId] && convMap[f.userId].unread" class="badge">{{ convMap[f.userId].unread }}</text>
          </view>
          <view v-if="convMap[f.userId]" class="fpreview">{{ convMap[f.userId].lastContent }}</view>
        </view>
        <text class="mini-btn danger" @tap.stop="del(f.userId)">删除</text>
      </view>
    </view>
```

- [ ] **Step 3: 补样式**(`<style scoped>` 末尾追加)

```css
.fmain { flex: 1; min-width: 0; }
.fpreview { font-size: 24rpx; color: var(--c-muted, #8A9A90); margin-top: 6rpx;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.badge { display: inline-block; min-width: 32rpx; height: 32rpx; line-height: 32rpx; text-align: center;
  padding: 0 8rpx; margin-left: 12rpx; border-radius: 16rpx; background: #E06A5B; color: #fff; font-size: 22rpx; }
```

- [ ] **Step 4: 提交**

```bash
git add miniprogram/src/pages/friends/friends.vue
git commit -m "feat(mp): 好友页会话列表(未读角标+末条预览+进聊天)"
```

- [ ] **Step 5: 手动验证(Task 7 + 8 一起)**

微信开发者工具双开两个账号(不同 X-WX-OPENID),互为好友后:
1. A 点好友进聊天页,发一条 → A 立即上屏(绿色右侧气泡)。
2. B 好友页 10s 内出现未读角标 + 末条预览;点进聊天页,3s 内看到 A 的消息(白色左侧气泡),角标清零。
3. B 回一条 → A 聊天页 3s 内收到。
4. 删好友后再进聊天页发送 → toast 报「对方不是你的好友」。
5. 离开聊天页(返回)后无报错(定时器已清)。

---

## 上线注意(实现完成后)

- 新增表 `chat_message`:新库由 schema.sql 自动建;**生产老库需手动执行 Task 2 的建表语句**(与 couple/friends 同惯例)。
- 合并前更新 memory `friends-feature.md`:聊天层已实现(轮询方案)。
