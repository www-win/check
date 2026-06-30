# 情侣关系功能 设计文档

- 日期：2026-06-30
- 范围：后端 API + 微信小程序端（Web 端本次不做）
- 状态：已确认，待实现

## 1. 目标

让两个用户建立"情侣"关系，建立后：

1. **互相看到对方打卡** —— 今日打卡状态（含心情、笔记、图片）、连续/累计/积分、整月日历。
2. **情侣组队 / 共同统计** —— 共同打卡天数、两人各自连续天数、合计积分。
3. **应用内互动** —— "戳一下 / 留言督促"，对方在情侣页内看到未读提示。

配对方式：**邀请码 + 对方同意**。一个用户同一时间最多只有一个情侣关系。

## 2. 关键约束与决策

- 双向对等：双方都能看对方数据；解除关系后立即互相不可见。
- 对方打卡内容**全部可见**（心情、笔记、图片都可见）。
- 越权防护：所有"对方数据"接口先校验当前用户存在 active 关系并取出 partnerId，只能看自己伴侣的数据。
- 戳一下未读提示**仅在情侣页内显示**，不做小程序角标、不做微信订阅消息推送（后续可补）。
- 邀请码复用 `user.invite_code` 字段，首次需要时懒生成。

## 3. 数据模型

新增 2 张表（追加到 `backend/src/main/resources/db/schema.sql`）。

```sql
-- 情侣关系
CREATE TABLE IF NOT EXISTS `couple` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `requester_id` BIGINT   NOT NULL COMMENT '发起绑定方（输入了对方邀请码）',
  `target_id`    BIGINT   NOT NULL COMMENT '邀请码拥有方（待同意方）',
  `status`       TINYINT  NOT NULL COMMENT '0=待确认 1=已建立',
  `created_at`   DATETIME NOT NULL,
  `confirmed_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_requester` (`requester_id`),
  KEY `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 情侣互动（戳一下/留言）
CREATE TABLE IF NOT EXISTS `couple_poke` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `couple_id`  BIGINT   NOT NULL,
  `from_user`  BIGINT   NOT NULL,
  `to_user`    BIGINT   NOT NULL,
  `message`    VARCHAR(200) NULL COMMENT '空=纯戳一下，有值=留言督促',
  `created_at` DATETIME NOT NULL,
  `read_at`    DATETIME NULL COMMENT '对方已读时间',
  PRIMARY KEY (`id`),
  KEY `idx_couple` (`couple_id`),
  KEY `idx_to_unread` (`to_user`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`user.invite_code` 已存在，不改表结构；仅补一个唯一索引建议（可选）：`UNIQUE KEY uk_invite_code (invite_code)`。

### 状态机

- `NONE`：当前用户无任何 active，也无 pending。
- `PENDING_OUT`：自己作为 requester 发起了待确认请求。
- `PENDING_IN`：自己作为 target 收到了待确认请求。
- `ACTIVE`：存在 status=1 的关系。

约束（Service 逻辑保证）：
- `bind` / `accept` 前，校验**双方都没有 active 关系**，否则报错。
- 同一用户同一时间最多一条 pending（再次发起前先清理/拒绝旧的，或直接报错提示）。

## 4. 后端接口

新建 `com.studybuddy.couple` 包，遵循现有 Controller/Service/entity/mapper/dto 分层、`R<T>` 包装、`CurrentUser.get()` 取 userId、`BizException` 抛错。

| 方法 | 路径 | 作用 |
|---|---|---|
| GET | `/api/couple` | 关系总览：`status`(NONE/PENDING_OUT/PENDING_IN/ACTIVE)、`myInviteCode`、`partner`{nickname,avatar}、`unreadPokeCount` |
| POST | `/api/couple/bind` | body `{inviteCode}`，按码找到 target，校验后建 pending |
| POST | `/api/couple/accept` | target 同意收到的请求 → status=1 |
| POST | `/api/couple/reject` | target 拒绝请求（删除/置废） |
| POST | `/api/couple/cancel` | requester 取消自己发出的 pending |
| DELETE | `/api/couple` | 解除 active 关系 |
| GET | `/api/couple/partner/status` | 对方 `CheckinStatusResp`（复用 `CheckinService.status(partnerId)`） |
| GET | `/api/couple/partner/calendar?month=YYYY-MM` | 对方某月日历（复用 `CheckinService.calendar(partnerId, month)`） |
| POST | `/api/couple/poke` | body `{message?}`，写一条 couple_poke |
| GET | `/api/couple/summary` | 共同统计：`commonDays`(两人同一天都打卡的天数)、`myStreak`/`partnerStreak`、`totalPoints`(两人积分合计) |

> 共同统计单独走 `GET /api/couple/summary`，保持 `GET /api/couple` 轻量、只负责关系总览。

### 关键查询

- 共同打卡天数：`checkin_record` 中 user_id ∈ {a,b}，按 `checkin_date` 分组，`COUNT(DISTINCT user_id)=2` 的天数。
- 未读戳数：`couple_poke` 中 `to_user=当前用户 AND read_at IS NULL`；进入对方相关页面或调用 poke 列表时标记已读。

### 邀请码生成

`UserService` 增加 `ensureInviteCode(userId)`：若 `invite_code` 为空，生成 6 位大写字母+数字（去除易混字符）唯一码，落库返回。`GET /api/couple` 内部调用以保证当前用户有码。

## 5. 小程序前端

- 新页面 `miniprogram/src/pages/couple/couple.vue`，在 `pages.json` 注册（非 tabBar，普通页）。
- "我的"页（`profile.vue`）新增一行入口"💑 情侣空间" → `uni.navigateTo` 到 couple 页。
- couple 页按 `status` 渲染：
  - **NONE**：显示我的邀请码（可点击复制）+ 输入框填对方邀请码 → 绑定。
  - **PENDING_OUT**：「等待 TA 同意」+ 取消按钮。
  - **PENDING_IN**：「TA 想和你组成情侣」+ 同意 / 拒绝。
  - **ACTIVE**：
    - 对方今日打卡卡片（状态 + 心情 + 笔记 + 图片）。
    - 共同统计卡片（共同打卡天数 / 各自连续 / 合计积分）。
    - 「查看 TA 的日历」→ 复用日历渲染展示对方月历。
    - 「戳一下 / 留言督促」按钮；显示对方戳我的未读提示。
    - 「解除关系」（二次确认）。
- `utils/request.js` 增加业务封装：`getCouple`、`bindCouple`、`acceptCouple`、`rejectCouple`、`cancelCouple`、`unbindCouple`、`getPartnerStatus`、`getPartnerCalendar`、`getCoupleSummary`、`pokePartner`。
- 复用现有 `toast`、`onShow` 刷新模式、`theme.css`/`uni.scss` 卡片样式。

## 6. 测试

- 后端单元/集成：绑定流程状态机（bind→accept/reject/cancel/unbind）、双方已有关系时的拦截、越权访问对方数据被拒、共同打卡天数计算、未读戳计数。
- 沿用现有 `backend/src/test` 的 JUnit 风格。

## 7. 不做（YAGNI / 后续）

- 微信订阅消息推送。
- Web 端 UI。
- 历史/前任关系记录、多段关系。
- 情侣共同目标（study_goal 暂不联动，后续可加）。
