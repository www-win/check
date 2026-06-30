# 成就徽章 设计

日期:2026-06-30

## 背景与目标

把打卡的瞬时奖励(里程碑积分)升级为可收藏的长期目标。用户在完成连续打卡、丰富打卡内容、绑定情侣等行为后,解锁对应徽章并获得奖励积分,在"我的成就"页查看已解锁/未解锁的徽章墙。

本功能是"强烈推荐三件套"的第 1 个(徽章 → 积分商城 → 打卡提醒),独立交付。

## 核心原则:独立模块,零侵入

- 新建独立模块 `com.studybuddy.achievement`,**不改动** checkin / couple 的现有写入流程。
- 徽章采用**懒解锁(lazy evaluation)**:每次拉取徽章接口时,后端基于现有数据重新评估,把新满足条件的徽章解锁并发奖励积分。
- 解锁**幂等**:已解锁的徽章不重复发分(靠 `(user_id, badge_code)` 唯一键 + 先查已解锁集合)。
- 老用户首次打开成就页即自动补齐应得徽章。

### 取舍说明

徽章不在"打卡那一刻"弹出,而是在用户**打开成就页 / 个人页**(触发徽章接口)时解锁并提示。换取:零侵入、无循环依赖、实现简单、老数据自动兼容。

## 徽章目录(写死在代码里,共 10 个)

每个徽章字段:`code`(枚举名)、`title`、`icon`(emoji)、`desc`、`rewardPoints`、`condition`(基于指标的判定)。

### 连续打卡(基于 `maxStreak`,撤销/断签后已得徽章不丢)

| code | title | icon | 条件 | 奖励 |
|------|-------|------|------|------|
| STREAK_3 | 坚持三天 | 🌱 | maxStreak ≥ 3 | 20 |
| STREAK_7 | 一周不断 | 🔥 | maxStreak ≥ 7 | 50 |
| STREAK_30 | 满月坚持 | 🌙 | maxStreak ≥ 30 | 150 |
| STREAK_100 | 百日传奇 | 👑 | maxStreak ≥ 100 | 500 |

### 习惯丰富度

| code | title | icon | 条件 | 奖励 |
|------|-------|------|------|------|
| FIRST_CHECKIN | 初次打卡 | ✅ | totalDays ≥ 1 | 10 |
| FIRST_PHOTO | 影像记录 | 📷 | 存在 image_url 非空的记录 | 20 |
| FIRST_NOTE | 文字心声 | 📝 | 存在 note 非空的记录 | 20 |
| MOOD_10 | 心情观察家 | 😊 | mood 非空的记录数 ≥ 10 | 30 |

### 情侣类

| code | title | icon | 条件 | 奖励 |
|------|-------|------|------|------|
| COUPLE_BOUND | 心有灵犀 | 💑 | 存在 status=1 的 couple(自己为 requester 或 target) | 50 |
| COUPLE_7 | 双向奔赴 | 💞 | 与伴侣共同打卡天数 ≥ 7 | 100 |

## 指标(AchievementMetrics)

一个 POJO,字段:`maxStreak`、`totalDays`、`hasPhoto`、`hasNote`、`moodCount`、`coupleBound`、`commonDays`。

计算来源(均由 AchievementService 直接查 mapper):
- `maxStreak`/`totalDays`:`checkinStatMapper.selectById(userId)`,null 时取 0。
- `hasPhoto`:`checkinRecordMapper.selectCount(user_id=me AND image_url IS NOT NULL) > 0`。
- `hasNote`:`checkinRecordMapper.selectCount(user_id=me AND note IS NOT NULL) > 0`。
- `moodCount`:`checkinRecordMapper.selectCount(user_id=me AND mood IS NOT NULL)`。
- `coupleBound` + 伴侣 id:内联查 `coupleMapper.selectOne(status=1 AND (requester_id=me OR target_id=me) limit 1)`;非空则 coupleBound=true,伴侣 id = 另一方。
- `commonDays`:若已绑定,`checkinRecordMapper.countCommonDays(me, partnerId)`(已存在,couple 模块在用);未绑定为 0。

## 数据模型

新表 `user_achievement`:

```sql
CREATE TABLE IF NOT EXISTS `user_achievement` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`        BIGINT       NOT NULL,
  `badge_code`     VARCHAR(40)  NOT NULL,
  `unlocked_at`    DATETIME     NOT NULL,
  `points_awarded` INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_badge` (`user_id`, `badge_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

实体 `UserAchievement`(`@TableName("user_achievement")`,id 自增),mapper `UserAchievementMapper extends BaseMapper<UserAchievement>`。

## 接口

`GET /api/achievements`(需登录,取 CurrentUser)。

响应 `AchievementListResp`:
```
{
  unlockedCount: int,
  totalCount: int,
  badges: [
    { code, title, icon, desc, rewardPoints, unlocked: bool, unlockedAt: datetime|null }
  ],
  newlyUnlocked: [ code, ... ]   // 本次评估新解锁的,供前端庆祝提示
}
```

`badges` 按目录固定顺序返回(连续 → 习惯 → 情侣)。

## 服务逻辑 AchievementService

注入:`CheckinStatMapper`、`CheckinRecordMapper`、`CoupleMapper`、`UserAchievementMapper`。

`evaluate(Long userId): List<Badge>`(`@Transactional`):
1. 算 `AchievementMetrics`。
2. 查该用户已解锁的 `badge_code` 集合。
3. 遍历 `Badge` 枚举:未解锁 且 `condition(metrics)==true` → 收集为新解锁。
4. 对每个新解锁:插入 `user_achievement`(unlocked_at=now, points_awarded=rewardPoints);把奖励积分加到 `checkin_stat.points`(用 `ensureStat` 逻辑:无 stat 先建)。
   - 累计奖励一次性加,`updatedAt` 刷新。
5. 返回新解锁的 Badge 列表。

`list(Long userId): AchievementListResp`:
1. `evaluate(userId)` 得到 newlyUnlocked。
2. 查已解锁集合(含本次新解锁)→ 组装全目录 badges(unlocked/unlockedAt)。
3. 返回。

> 注:积分累加直接复用一份与 CheckinService 等价的 ensureStat 逻辑(achievement 模块内自有一份私有方法,避免跨模块耦合)。

## 前端(两端共用接口)

### Web(frontend)
- 路由 `router.js` 加 `{ path: '/achievements', component: () => import('./views/Achievements.vue') }`。
- `Profile.vue` 在情侣入口附近加一个入口卡:`🏅 我的成就  X/Y  ›`,点击 `router.push('/achievements')`。X/Y 通过调 `/achievements` 接口的 `unlockedCount/totalCount` 得到。
- 新建 `Achievements.vue`:网格展示全部徽章。已解锁:亮色 + 图标 + 标题 + 解锁日期;未解锁:灰色 + 图标暗淡 + 条件文案(desc)。若返回 `newlyUnlocked` 非空,`toast('🎉 解锁新徽章')`。

### 小程序(miniprogram)
- `pages.json` 加 `pages/achievements/achievements`(navigationBarTitleText: 我的成就)。
- `profile.vue` 加同样的入口,`uni.navigateTo` 到成就页。
- 新建 `pages/achievements/achievements.vue`:同 Web 的网格展示;`onShow` 拉取;`newlyUnlocked` 非空则 `toast`。
- API 封装 `utils/request.js` 加 `getAchievements = () => request('/achievements')`。
- Web `api.js` 加 `getAchievements() { return request('/achievements') }`。

## 测试

`AchievementServiceTest`(JUnit5 + Mockito,mock 四个 mapper):
1. 指标满足多个条件 → `evaluate` 解锁对应徽章、各发对应积分、累加到 stat。
2. 幂等:已解锁集合包含某徽章 → 不重复插入、不重复发分。
3. 条件不满足 → 不解锁(如 maxStreak=2 不解锁 STREAK_3)。
4. 无 stat 用户 → ensureStat 建 stat 后再发分。
5. `list` 返回的 `totalCount` 等于目录大小、`unlockedCount` 正确、badges 顺序固定。

## 数据库迁移

`schema.sql` 末尾追加上面的 `CREATE TABLE IF NOT EXISTS user_achievement`(幂等、单句、无存储过程,符合 Spring sql.init 约束)。生产老库可单独执行同一句。

## 非目标(YAGNI)

- 不做后台可配置徽章目录(写死代码)。
- 不做徽章分享卡片(后续可加)。
- 不做"打卡瞬间弹窗解锁"(采用懒解锁)。
- 不做累计天数维度徽章(本期未选)。
