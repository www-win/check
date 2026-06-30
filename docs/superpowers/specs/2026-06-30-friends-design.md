# 好友关系 设计

日期:2026-06-30

## 背景与目标

为应用增加好友体系,作为后续"好友私聊"的基础。用户通过邀请码互加好友(需对方同意),维护好友列表。本功能是"好友 + 聊天"两个子系统中的第 1 个,独立交付;聊天单独设计实现。

## 范围

- 加好友:输入对方邀请码发起请求 → 对方同意才成为好友。
- 好友列表、收到/发出的请求管理(同意/拒绝/撤回)、删除好友。
- 本轮前端仅小程序端 + 后端(与"修改昵称"一致)。
- 复用现有邀请码体系(`user.invite_code`,`UserService.ensureInviteCode`)。

## 后端设计(新模块 com.studybuddy.friend)

### 数据表 friendship

无向好友关系 + 待确认请求(照搬 couple 模式,但允许一个用户有多个好友/请求):

```sql
CREATE TABLE IF NOT EXISTS `friendship` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `requester_id` BIGINT   NOT NULL COMMENT '发起方(输入了对方邀请码)',
  `addressee_id` BIGINT   NOT NULL COMMENT '被加方(待同意方)',
  `status`       TINYINT  NOT NULL COMMENT '0=待确认 1=已成为好友',
  `created_at`   DATETIME NOT NULL,
  `accepted_at`  DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_req_addr` (`requester_id`, `addressee_id`),
  KEY `idx_requester` (`requester_id`),
  KEY `idx_addressee` (`addressee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- 已成为好友:status=1,无向(查"我的好友"= status=1 且 requester=me 或 addressee=me)。
- 收到的请求:status=0 且 addressee=me。发出的请求:status=0 且 requester=me。
- 唯一键 `(requester_id, addressee_id)` 防同方向重复请求;反方向重复由 service 校验。

实体 `Friendship`(`@TableName("friendship")`,id 自增),mapper `FriendshipMapper extends BaseMapper<Friendship>`。

### 接口(均需登录,/api/friends)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/friends` | 一次性返回好友 + 双向请求 + 我的邀请码 |
| POST | `/api/friends/requests` | body `{ inviteCode }`,用邀请码发好友请求 |
| POST | `/api/friends/requests/{id}/accept` | 同意(仅被加方) |
| POST | `/api/friends/requests/{id}/reject` | 拒绝(仅被加方) |
| POST | `/api/friends/requests/{id}/cancel` | 撤回(仅发起方) |
| DELETE | `/api/friends/{friendUserId}` | 删除好友 |

`GET /api/friends` 响应 `FriendListResp`:
```
{
  myInviteCode: string,
  friends:  [ { userId, nickname, avatar } ],
  incoming: [ { requestId, userId, nickname, avatar } ],  // 别人加我,待我处理
  outgoing: [ { requestId, userId, nickname, avatar } ]   // 我加别人,待对方处理
}
```

### FriendService(仿 CoupleService)

注入:`FriendshipMapper`、`UserMapper`、`UserService`。

- `list(me)`:`myInviteCode = userService.ensureInviteCode(me)`;查 status=1 涉及 me 的行 → 好友(对方 = 另一端,取昵称头像);查 status=0 addressee=me → incoming;status=0 requester=me → outgoing。
- `request(me, inviteCode)`:
  1. 按 `inviteCode`(trim+大写)查目标用户,无 → `41404 邀请码无效`。
  2. 目标==me → `41400 不能加自己为好友`。
  3. 已是好友(status=1 任一方向)→ `41401 你们已经是好友`。
  4. 已存在待确认(任一方向)→ `41403 已发送过请求,等待对方同意`。
  5. 插入 `friendship(requester=me, addressee=target, status=0, created_at=now)`。
- `accept(me, requestId)`:查该 id 的 pending 行,addressee 必须==me 否则 `41405`;set status=1, accepted_at=now, updateById。
- `reject(me, requestId)`:同上校验(addressee==me),deleteById。
- `cancel(me, requestId)`:查 pending 行,requester 必须==me 否则 `41405`;deleteById。
- `removeFriend(me, friendUserId)`:删除 status=1 且 {requester=me,addressee=friend} 或 {requester=friend,addressee=me} 的行;无则 `41405`(无需报错也可,但统一抛便于前端提示)。

### 错误码

| 码 | 含义 |
|----|------|
| 41400 | 不能加自己为好友 |
| 41401 | 你们已经是好友 |
| 41403 | 已发送过请求,等待对方同意 |
| 41404 | 邀请码无效 |
| 41405 | 请求不存在或无权操作 |

## 小程序前端

- 个人页(profile.vue)加入口 `👥 我的好友 ›` → `uni.navigateTo` 到好友页。
- 新建页 `pages/friends/friends`(pages.json 注册):
  - 顶部:展示"我的邀请码"(可复制)+ "输入邀请码加好友"按钮(`uni.showModal` editable 输入码 → 调 request)。
  - 好友列表:头像 + 昵称 + "删除"(`uni.showModal` 确认后调 DELETE)。
  - 收到的请求:头像 + 昵称 + "同意"/"拒绝"。
  - 发出的请求:头像 + 昵称 + "撤回"。
  - `onShow` 拉取 `GET /api/friends`;每次操作后刷新。
- `utils/request.js` 加封装:`getFriends`、`addFriend(inviteCode)`、`acceptFriend(id)`、`rejectFriend(id)`、`cancelFriend(id)`、`removeFriend(userId)`。

## 测试

`FriendServiceTest`(JUnit5 + Mockito,mock FriendshipMapper/UserMapper/UserService):
1. `request` 正常 → 插入 pending 行。
2. 无效邀请码 → 41404。
3. 加自己 → 41400。
4. 已是好友 → 41401。
5. 已有待确认请求(任一方向)→ 41403。
6. `accept` 正常 → status=1;非被加方 → 41405。
7. `reject` 正常删除;`cancel` 正常删除、非发起方 → 41405。
8. `removeFriend` 正常删除;无关系 → 41405。
9. `list` 正确分类 friends/incoming/outgoing。

## 数据库迁移

`schema.sql` 末尾追加 `friendship` 建表(幂等、单句、无存储过程,符合 Spring sql.init 约束);生产老库单独执行同一句。

## 非目标(YAGNI)

- 不做昵称搜索加好友(本期仅邀请码)。
- 不做好友备注、分组、拉黑。
- 不做 Web 端(本轮仅小程序;计划保留可后续补)。
- 聊天/私信单独设计(下一个子系统)。
