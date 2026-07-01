# 好友私聊(文字聊天) 设计文档

- 日期:2026-07-01
- 范围:后端 API + 微信小程序端(Web 端本次不做)
- 状态:已确认,待实现

## 1. 目标

让互为好友(friendship status=1)的两个用户进行**一对一文字私聊**:

1. **发文字消息** —— 纯文本,1000 字以内。
2. **聊天窗口** —— 进好友的聊天页看历史(最近 N 条)+ 轮询接收新消息,自己发的即时上屏。
3. **会话列表** —— 复用好友页,每个好友挂未读角标 + 末条消息预览。
4. **未读** —— 进聊天页/收到新消息时清零;不做"对方已读"回执。

场景:异地情侣/好友的日常文字沟通。图片、语音、打卡卡片分享等留待后续迭代。

## 2. 关键约束与决策

- **仅 status=1 好友可聊**:每个接口先校验 `peerId` 与当前用户是好友,复用 `FriendshipMapper`。
- **纯文本 MVP**:不做图片/语音/表情包。
- **轮询,无长连接**:微信云托管 callContainer 无长连接,聊天页 3s、好友页 10s 轮询。
- **增量拉取用 id 游标**:聊天页轮询传本地最大 `afterId`,天然去重、天然不重复拉自己刚发的。
- **未读汇总走聚合查询**:方案 1(单表 + is_read),低并发下聚合代价可忽略,不引入会话冗余表。
- **不做已读回执**:只有"我的未读数",一对一场景已读回执反成压力。
- **不做上拉加载更早历史**:MVP 只做首屏最近 N 条 + 轮询增量。以后需要加 `beforeId` 即可,不改表。
- **删好友**:历史消息保留;发消息因 status 校验被拒;会话列表只列仍是好友的会话。

## 3. 数据模型

新增 1 张表(追加到 `backend/src/main/resources/db/schema.sql`,`CREATE TABLE IF NOT EXISTS`,遵循 couple/friends 迁移惯例:新库自动建,生产老库需手动执行本建表语句)。

```sql
CREATE TABLE IF NOT EXISTS chat_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_id   BIGINT        NOT NULL,
  receiver_id BIGINT        NOT NULL,
  content     VARCHAR(1000) NOT NULL,
  is_read     TINYINT       NOT NULL DEFAULT 0,   -- 0未读 1已读(receiver 视角)
  created_at  DATETIME      NOT NULL,
  KEY idx_pair_time (sender_id, receiver_id, id),
  KEY idx_unread (receiver_id, is_read)
);
```

- `idx_pair_time`:拉某会话历史 / 增量(按 id 游标)。
- `idx_unread`:未读汇总 `WHERE receiver_id=? AND is_read=0`。
- `content`:后端 trim 后判非空、长度 ≤ 1000。
- `created_at`:后端 `LocalDateTime.now()` 落库,前端只展示。

## 4. 后端模块结构

新增独立模块 `com.studybuddy.chat`(照搬 friend/couple 分包):

```
chat/
  ChatController.java
  ChatService.java
  entity/ChatMessage.java
  dto/  SendMsgReq / ChatMessageInfo / ConversationInfo
  mapper/ChatMessageMapper.java
```

## 5. 接口设计

全部挂 `/api/chat`,统一 `R` 返回,`CurrentUser.get()` 取登录态;均要求登录 + `peerId` 是 status=1 好友。

| 方法 | 路径 | 作用 | 说明 |
|---|---|---|---|
| GET | `/api/chat/conversations` | 会话列表 | 返回每个有聊天记录的好友:`peerUserId, 末条内容, 末条时间, 未读数`。好友页轮询刷新角标。只列仍是 status=1 好友的会话。 |
| GET | `/api/chat/messages?peerId=&afterId=&limit=` | 拉消息 | `afterId` 缺省=取最近 `limit` 条(倒序取、正序返回,首屏用);`afterId>0`=取该 id 之后的新消息(聊天页轮询增量)。 |
| POST | `/api/chat/messages` | 发消息 | body `{peerId, content}`,落库后返回该消息(含 id、created_at),本人即时上屏。 |
| POST | `/api/chat/read` | 标记已读 | body `{peerId}`,把该好友发给我且 is_read=0 的置 1。进聊天页/收到新消息时调。 |

`limit` 默认值建议 30(首屏最近 30 条)。

**错误码**(沿用段位风格,friend 占 41400~41405,chat 用 414 段的 41410 起):

- 41410 对方不是你的好友
- 41411 消息内容为空 / 超长
- 41412 不能给自己发消息

## 6. 前端(小程序)

**新增页面** `miniprogram/src/pages/chat/chat.vue`:从好友页某好友点进,带 `peerId` + 昵称。

- 进入:`GET /messages?peerId=` 首屏最近 N 条 → 渲染 → 滚到底 → `POST /read`。
- 轮询(每 3s):`GET /messages?peerId=&afterId=<本地最大id>`,有新消息就追加、滚到底、`POST /read`。
- 发送:`POST /messages` → 成功把返回消息追加到列表(即时上屏,不等轮询);发送中按钮置灰兜底。
- 离开(`onHide`/`onUnload`):清除定时器。
- 空态:无消息显示"打个招呼吧"。

**好友页改造** `pages/friends`:

- `onShow` + 每 10s 轮询 `/conversations`,给每个好友挂未读角标 + 末条预览。
- 点好友 → 跳 `chat` 页。

轮询间隔(聊天页 3s、好友页 10s)写成常量便于调整。

## 7. 边界与错误处理

- **删好友后**:历史保留;发消息 status 校验被拒(41410);会话列表 join friendship status=1 过滤掉。
- **给自己发**:41412。
- **空 / 超长内容**:41411(trim 后判空 + 长度)。
- **并发重复点发送**:每次 POST 独立插入,前端发送中置灰兜底;纯文本重复无害,不做服务端去重。
- **未读汇总性能**:低并发聚合可接受;`ChatService` 只读查询加 `@Transactional(readOnly=true)`(补齐 friend 模块欠的惯例)。

## 8. 测试

Service 单测(照 friend/couple 风格):

- 发消息:成功落库 / 非好友被拒(41410) / 给自己(41412) / 空内容(41411)。
- 拉消息:首屏最近 N 条正序 / afterId 增量只返回更新的。
- 未读:发一条对方未读 +1 → 对方 `/read` 后清零。
- 会话列表:末条内容与未读数正确 / 删好友后该会话不在列表。

前端手动验证:开发者工具双开两个 openid 互发。

## 9. 上线注意

- 新增表 `chat_message`。schema.sql 含 `CREATE TABLE IF NOT EXISTS`(新库自动建);**生产老库需手动执行该建表语句**。与 couple/friends 同款迁移惯例。
- schema.sql 只放逐句、幂等、无 DELIMITER 的语句(遵循既有约束)。

## 10. 不做(YAGNI,后续可补)

- 图片 / 语音 / 表情 / 打卡卡片分享。
- 上拉加载更早历史(加 `beforeId` 即可)。
- 已读回执。
- 消息撤回 / 删除。
- 全局 tabBar 红点、微信订阅消息推送。
- 群聊 / 多人会话。
