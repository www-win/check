# 成就徽章 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增独立的成就徽章模块:用户达成连续打卡/习惯/情侣条件后懒解锁徽章并发奖励积分,在"我的成就"页(Web + 小程序)查看徽章墙。

**Architecture:** 新建 `com.studybuddy.achievement` 模块,不改动 checkin/couple 写入流程。`MetricsCalculator` 负责从现有表查"指标",`AchievementService` 负责"按指标解锁徽章+发分"(懒解锁、幂等)。`GET /api/achievements` 评估并返回全目录。前端两端各加个人页入口 + 独立徽章页。

**Tech Stack:** Spring Boot + MyBatis-Plus + JUnit5/Mockito(后端);Vue 3(Web);uni-app/Vue 3(小程序)。

## Global Constraints

- 新建独立模块 `com.studybuddy.achievement`,**不修改** checkin/couple 的写入流程(避免循环依赖)。
- 徽章目录写死在代码(`Badge` 枚举),共 **10 个**,顺序固定:STREAK_3, STREAK_7, STREAK_30, STREAK_100, FIRST_CHECKIN, FIRST_PHOTO, FIRST_NOTE, MOOD_10, COUPLE_BOUND, COUPLE_7。
- 各徽章 title/icon/desc/rewardPoints 见 Task 1,**逐字使用**。
- 懒解锁:拉取接口时评估;解锁**幂等**(靠 `(user_id, badge_code)` 唯一键 + 先查已解锁集合)。
- 连续类徽章用 `maxStreak`(不回退)。
- 接口路径 `GET /api/achievements`,两端 `request('/achievements')`。
- `schema.sql` 追加语句必须幂等、单句、无存储过程(Spring sql.init 每次启动执行)。
- 后端构建/测试从仓库根执行;若 PATH 未带工具链,先注入:
  `$env:JAVA_HOME="$env:USERPROFILE\tools\jdk-17.0.19+10"; $env:Path="$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.9\bin;$env:Path"`

---

### Task 1: 徽章目录 + 数据层

**Files:**
- Create: `backend/src/main/java/com/studybuddy/achievement/AchievementMetrics.java`
- Create: `backend/src/main/java/com/studybuddy/achievement/Badge.java`
- Create: `backend/src/main/java/com/studybuddy/achievement/entity/UserAchievement.java`
- Create: `backend/src/main/java/com/studybuddy/achievement/mapper/UserAchievementMapper.java`
- Modify: `backend/src/main/resources/db/schema.sql`
- Test: `backend/src/test/java/com/studybuddy/achievement/BadgeTest.java`

**Interfaces:**
- Produces:
  - `AchievementMetrics`(public 字段 `int maxStreak; int totalDays; boolean hasPhoto; boolean hasNote; int moodCount; boolean coupleBound; int commonDays;`)
  - `Badge` 枚举(public final 字段 `title/icon/desc/rewardPoints`,方法 `boolean satisfied(AchievementMetrics m)`)
  - `UserAchievement` 实体(`id/userId/badgeCode/unlockedAt/pointsAwarded`)
  - `UserAchievementMapper extends BaseMapper<UserAchievement>`

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/studybuddy/achievement/BadgeTest.java`:

```java
package com.studybuddy.achievement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeTest {

    private AchievementMetrics metrics() {
        return new AchievementMetrics();
    }

    @Test
    void catalogHasTenBadgesInFixedOrder() {
        Badge[] all = Badge.values();
        assertEquals(10, all.length);
        assertEquals(Badge.STREAK_3, all[0]);
        assertEquals(Badge.COUPLE_7, all[9]);
    }

    @Test
    void streakBadgesUseMaxStreak() {
        AchievementMetrics m = metrics();
        m.maxStreak = 7;
        assertTrue(Badge.STREAK_3.satisfied(m));
        assertTrue(Badge.STREAK_7.satisfied(m));
        assertFalse(Badge.STREAK_30.satisfied(m));
    }

    @Test
    void habitBadgesReadFlags() {
        AchievementMetrics m = metrics();
        m.totalDays = 1;
        m.hasPhoto = true;
        m.hasNote = false;
        m.moodCount = 10;
        assertTrue(Badge.FIRST_CHECKIN.satisfied(m));
        assertTrue(Badge.FIRST_PHOTO.satisfied(m));
        assertFalse(Badge.FIRST_NOTE.satisfied(m));
        assertTrue(Badge.MOOD_10.satisfied(m));
    }

    @Test
    void coupleBadges() {
        AchievementMetrics m = metrics();
        m.coupleBound = true;
        m.commonDays = 6;
        assertTrue(Badge.COUPLE_BOUND.satisfied(m));
        assertFalse(Badge.COUPLE_7.satisfied(m));
        m.commonDays = 7;
        assertTrue(Badge.COUPLE_7.satisfied(m));
    }

    @Test
    void rewardPointsAsSpecified() {
        assertEquals(500, Badge.STREAK_100.rewardPoints);
        assertEquals(10, Badge.FIRST_CHECKIN.rewardPoints);
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=BadgeTest test`
Expected: 编译失败(`Badge`/`AchievementMetrics` 不存在)。

- [ ] **Step 3: 创建 AchievementMetrics**

创建 `backend/src/main/java/com/studybuddy/achievement/AchievementMetrics.java`:

```java
package com.studybuddy.achievement;

/** 评估徽章所需的各项指标。字段 public,供 Badge 的判定 lambda 直接读取。 */
public class AchievementMetrics {
    public int maxStreak;
    public int totalDays;
    public boolean hasPhoto;
    public boolean hasNote;
    public int moodCount;
    public boolean coupleBound;
    public int commonDays;
}
```

- [ ] **Step 4: 创建 Badge 枚举**

创建 `backend/src/main/java/com/studybuddy/achievement/Badge.java`:

```java
package com.studybuddy.achievement;

import java.util.function.Predicate;

/** 徽章目录(固定 10 个,顺序即展示顺序)。 */
public enum Badge {
    STREAK_3("坚持三天", "🌱", "连续打卡 3 天", 20, m -> m.maxStreak >= 3),
    STREAK_7("一周不断", "🔥", "连续打卡 7 天", 50, m -> m.maxStreak >= 7),
    STREAK_30("满月坚持", "🌙", "连续打卡 30 天", 150, m -> m.maxStreak >= 30),
    STREAK_100("百日传奇", "👑", "连续打卡 100 天", 500, m -> m.maxStreak >= 100),
    FIRST_CHECKIN("初次打卡", "✅", "完成第一次打卡", 10, m -> m.totalDays >= 1),
    FIRST_PHOTO("影像记录", "📷", "第一次拍照打卡", 20, m -> m.hasPhoto),
    FIRST_NOTE("文字心声", "📝", "第一次写打卡笔记", 20, m -> m.hasNote),
    MOOD_10("心情观察家", "😊", "累计记录心情 10 次", 30, m -> m.moodCount >= 10),
    COUPLE_BOUND("心有灵犀", "💑", "成功绑定情侣", 50, m -> m.coupleBound),
    COUPLE_7("双向奔赴", "💞", "两人共同打卡 7 天", 100, m -> m.commonDays >= 7);

    public final String title;
    public final String icon;
    public final String desc;
    public final int rewardPoints;
    private final Predicate<AchievementMetrics> condition;

    Badge(String title, String icon, String desc, int rewardPoints, Predicate<AchievementMetrics> condition) {
        this.title = title;
        this.icon = icon;
        this.desc = desc;
        this.rewardPoints = rewardPoints;
        this.condition = condition;
    }

    public boolean satisfied(AchievementMetrics m) {
        return condition.test(m);
    }
}
```

- [ ] **Step 5: 创建 UserAchievement 实体**

创建 `backend/src/main/java/com/studybuddy/achievement/entity/UserAchievement.java`:

```java
package com.studybuddy.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_achievement")
public class UserAchievement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String badgeCode;
    private LocalDateTime unlockedAt;
    private Integer pointsAwarded;
}
```

- [ ] **Step 6: 创建 UserAchievementMapper**

创建 `backend/src/main/java/com/studybuddy/achievement/mapper/UserAchievementMapper.java`:

```java
package com.studybuddy.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.achievement.entity.UserAchievement;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserAchievementMapper extends BaseMapper<UserAchievement> {
}
```

- [ ] **Step 7: schema.sql 追加建表**

在 `backend/src/main/resources/db/schema.sql` 末尾(最后一行注释之后)追加:

```sql

-- 成就徽章(用户已解锁的徽章)
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

- [ ] **Step 8: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=BadgeTest test`
Expected: BUILD SUCCESS,BadgeTest 全部通过。

- [ ] **Step 9: 提交**

```bash
git add backend/src/main/java/com/studybuddy/achievement backend/src/main/resources/db/schema.sql backend/src/test/java/com/studybuddy/achievement/BadgeTest.java
git commit -m "feat: 成就徽章目录+数据层(Badge/Metrics/实体/表)"
```

---

### Task 2: MetricsCalculator(指标计算)

**Files:**
- Create: `backend/src/main/java/com/studybuddy/achievement/MetricsCalculator.java`
- Test: `backend/src/test/java/com/studybuddy/achievement/MetricsCalculatorTest.java`

**Interfaces:**
- Consumes: `AchievementMetrics`(Task 1);`CheckinStatMapper`、`CheckinRecordMapper`(含 `countCommonDays(Long,Long)`)、`CoupleMapper`(均为现有 `BaseMapper`);实体 `CheckinStat`、`CheckinRecord`、`Couple`。
- Produces: `@Component MetricsCalculator`,方法 `public AchievementMetrics compute(Long userId)`。查询顺序固定:stat → selectCount(image) → selectCount(note) → selectCount(mood) → couple。

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/studybuddy/achievement/MetricsCalculatorTest.java`:

```java
package com.studybuddy.achievement;

import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import com.studybuddy.couple.entity.Couple;
import com.studybuddy.couple.mapper.CoupleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsCalculatorTest {

    @Mock CheckinStatMapper statMapper;
    @Mock CheckinRecordMapper recordMapper;
    @Mock CoupleMapper coupleMapper;

    @InjectMocks MetricsCalculator calculator;

    private final Long uid = 1L;

    @Test
    void computesFromStatRecordsAndCouple() {
        CheckinStat stat = new CheckinStat();
        stat.setMaxStreak(8);
        stat.setTotalDays(5);
        when(statMapper.selectById(uid)).thenReturn(stat);
        // selectCount 调用顺序: image, note, mood
        when(recordMapper.selectCount(any())).thenReturn(2L, 0L, 10L);

        Couple c = new Couple();
        c.setRequesterId(uid);
        c.setTargetId(2L);
        c.setStatus(1);
        when(coupleMapper.selectOne(any())).thenReturn(c);
        when(recordMapper.countCommonDays(eq(uid), eq(2L))).thenReturn(7);

        AchievementMetrics m = calculator.compute(uid);

        assertEquals(8, m.maxStreak);
        assertEquals(5, m.totalDays);
        assertTrue(m.hasPhoto);   // 2 > 0
        assertFalse(m.hasNote);   // 0 > 0 == false
        assertEquals(10, m.moodCount);
        assertTrue(m.coupleBound);
        assertEquals(7, m.commonDays);
    }

    @Test
    void noStatAndNoCoupleGivesZeros() {
        when(statMapper.selectById(uid)).thenReturn(null);
        when(recordMapper.selectCount(any())).thenReturn(0L, 0L, 0L);
        when(coupleMapper.selectOne(any())).thenReturn(null);

        AchievementMetrics m = calculator.compute(uid);

        assertEquals(0, m.maxStreak);
        assertEquals(0, m.totalDays);
        assertFalse(m.hasPhoto);
        assertFalse(m.coupleBound);
        assertEquals(0, m.commonDays);
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=MetricsCalculatorTest test`
Expected: 编译失败(`MetricsCalculator` 不存在)。

- [ ] **Step 3: 实现 MetricsCalculator**

创建 `backend/src/main/java/com/studybuddy/achievement/MetricsCalculator.java`:

```java
package com.studybuddy.achievement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.checkin.entity.CheckinRecord;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import com.studybuddy.couple.entity.Couple;
import com.studybuddy.couple.mapper.CoupleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricsCalculator {
    private final CheckinStatMapper statMapper;
    private final CheckinRecordMapper recordMapper;
    private final CoupleMapper coupleMapper;

    public AchievementMetrics compute(Long userId) {
        AchievementMetrics m = new AchievementMetrics();

        CheckinStat stat = statMapper.selectById(userId);
        m.maxStreak = stat == null ? 0 : n(stat.getMaxStreak());
        m.totalDays = stat == null ? 0 : n(stat.getTotalDays());

        m.hasPhoto = recordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .isNotNull(CheckinRecord::getImageUrl)) > 0;
        m.hasNote = recordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .isNotNull(CheckinRecord::getNote)) > 0;
        long moods = recordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .isNotNull(CheckinRecord::getMood));
        m.moodCount = (int) moods;

        Couple couple = coupleMapper.selectOne(new LambdaQueryWrapper<Couple>()
                .eq(Couple::getStatus, 1)
                .and(w -> w.eq(Couple::getRequesterId, userId).or().eq(Couple::getTargetId, userId))
                .last("limit 1"));
        if (couple != null) {
            m.coupleBound = true;
            Long pid = couple.getRequesterId().equals(userId)
                    ? couple.getTargetId() : couple.getRequesterId();
            m.commonDays = recordMapper.countCommonDays(userId, pid);
        }
        return m;
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=MetricsCalculatorTest test`
Expected: BUILD SUCCESS,两个用例通过。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/studybuddy/achievement/MetricsCalculator.java backend/src/test/java/com/studybuddy/achievement/MetricsCalculatorTest.java
git commit -m "feat: 成就徽章指标计算 MetricsCalculator + 单测"
```

---

### Task 3: AchievementService(解锁+发分)+ DTO

**Files:**
- Create: `backend/src/main/java/com/studybuddy/achievement/dto/AchievementResp.java`
- Create: `backend/src/main/java/com/studybuddy/achievement/dto/AchievementListResp.java`
- Create: `backend/src/main/java/com/studybuddy/achievement/AchievementService.java`
- Test: `backend/src/test/java/com/studybuddy/achievement/AchievementServiceTest.java`

**Interfaces:**
- Consumes: `MetricsCalculator.compute(Long)`(Task 2);`Badge`、`AchievementMetrics`、`UserAchievement`、`UserAchievementMapper`(Task 1);`CheckinStatMapper`、`CheckinStat`(现有)。
- Produces:
  - `AchievementService.evaluate(Long userId): List<Badge>`(解锁新徽章、发分、返回新解锁列表)
  - `AchievementService.list(Long userId): AchievementListResp`
  - `AchievementResp(String code, String title, String icon, String desc, int rewardPoints, boolean unlocked, LocalDateTime unlockedAt)`
  - `AchievementListResp(int unlockedCount, int totalCount, List<AchievementResp> badges, List<String> newlyUnlocked)`

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/studybuddy/achievement/AchievementServiceTest.java`:

```java
package com.studybuddy.achievement;

import com.studybuddy.achievement.dto.AchievementListResp;
import com.studybuddy.achievement.entity.UserAchievement;
import com.studybuddy.achievement.mapper.UserAchievementMapper;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock MetricsCalculator metricsCalculator;
    @Mock UserAchievementMapper achievementMapper;
    @Mock CheckinStatMapper statMapper;

    @InjectMocks AchievementService service;

    private final Long uid = 1L;

    private AchievementMetrics metricsStreak7Photo() {
        AchievementMetrics m = new AchievementMetrics();
        m.maxStreak = 7;       // 解锁 STREAK_3(20) + STREAK_7(50)
        m.totalDays = 1;       // FIRST_CHECKIN(10)
        m.hasPhoto = true;     // FIRST_PHOTO(20)
        return m;              // 共 4 个,合计 100 分
    }

    @Test
    void evaluateUnlocksSatisfiedBadgesAndAwardsPoints() {
        when(metricsCalculator.compute(uid)).thenReturn(metricsStreak7Photo());
        when(achievementMapper.selectList(any())).thenReturn(Collections.emptyList());
        CheckinStat stat = new CheckinStat();
        stat.setUserId(uid);
        stat.setPoints(30);
        when(statMapper.selectById(uid)).thenReturn(stat);

        List<Badge> newly = service.evaluate(uid);

        assertEquals(4, newly.size());
        assertTrue(newly.contains(Badge.STREAK_7));
        verify(achievementMapper, times(4)).insert(any(UserAchievement.class));
        ArgumentCaptor<CheckinStat> cap = ArgumentCaptor.forClass(CheckinStat.class);
        verify(statMapper).updateById(cap.capture());
        assertEquals(130, cap.getValue().getPoints()); // 30 + 100
    }

    @Test
    void evaluateIsIdempotentForAlreadyUnlocked() {
        when(metricsCalculator.compute(uid)).thenReturn(metricsStreak7Photo());
        UserAchievement s3 = new UserAchievement();
        s3.setBadgeCode(Badge.STREAK_3.name());
        UserAchievement s7 = new UserAchievement();
        s7.setBadgeCode(Badge.STREAK_7.name());
        UserAchievement fc = new UserAchievement();
        fc.setBadgeCode(Badge.FIRST_CHECKIN.name());
        UserAchievement fp = new UserAchievement();
        fp.setBadgeCode(Badge.FIRST_PHOTO.name());
        when(achievementMapper.selectList(any())).thenReturn(List.of(s3, s7, fc, fp));

        List<Badge> newly = service.evaluate(uid);

        assertEquals(0, newly.size());
        verify(achievementMapper, never()).insert(any());
        verify(statMapper, never()).updateById(any());
    }

    @Test
    void evaluateCreatesStatWhenMissing() {
        AchievementMetrics m = new AchievementMetrics();
        m.totalDays = 1; // 仅 FIRST_CHECKIN(10)
        when(metricsCalculator.compute(uid)).thenReturn(m);
        when(achievementMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(statMapper.selectById(uid)).thenReturn(null);

        List<Badge> newly = service.evaluate(uid);

        assertEquals(1, newly.size());
        verify(statMapper).insert(any(CheckinStat.class));
        verify(statMapper).updateById(any(CheckinStat.class));
    }

    @Test
    void listReturnsFullCatalogWithStatus() {
        AchievementMetrics none = new AchievementMetrics(); // 不满足任何条件
        when(metricsCalculator.compute(uid)).thenReturn(none);
        when(achievementMapper.selectList(any())).thenReturn(Collections.emptyList());

        AchievementListResp resp = service.list(uid);

        assertEquals(10, resp.getTotalCount());
        assertEquals(0, resp.getUnlockedCount());
        assertEquals(10, resp.getBadges().size());
        assertEquals("STREAK_3", resp.getBadges().get(0).getCode());
        assertEquals(0, resp.getNewlyUnlocked().size());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=AchievementServiceTest test`
Expected: 编译失败(`AchievementService`/DTO 不存在)。

- [ ] **Step 3: 创建 AchievementResp**

创建 `backend/src/main/java/com/studybuddy/achievement/dto/AchievementResp.java`:

```java
package com.studybuddy.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AchievementResp {
    private String code;
    private String title;
    private String icon;
    private String desc;
    private int rewardPoints;
    private boolean unlocked;
    private LocalDateTime unlockedAt;
}
```

- [ ] **Step 4: 创建 AchievementListResp**

创建 `backend/src/main/java/com/studybuddy/achievement/dto/AchievementListResp.java`:

```java
package com.studybuddy.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AchievementListResp {
    private int unlockedCount;
    private int totalCount;
    private List<AchievementResp> badges;
    private List<String> newlyUnlocked;
}
```

- [ ] **Step 5: 实现 AchievementService**

创建 `backend/src/main/java/com/studybuddy/achievement/AchievementService.java`:

```java
package com.studybuddy.achievement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.achievement.dto.AchievementListResp;
import com.studybuddy.achievement.dto.AchievementResp;
import com.studybuddy.achievement.entity.UserAchievement;
import com.studybuddy.achievement.mapper.UserAchievementMapper;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {
    private final MetricsCalculator metricsCalculator;
    private final UserAchievementMapper achievementMapper;
    private final CheckinStatMapper statMapper;

    /** 评估并解锁新满足条件的徽章,发奖励积分。返回本次新解锁。 */
    @Transactional
    public List<Badge> evaluate(Long userId) {
        AchievementMetrics m = metricsCalculator.compute(userId);
        Set<String> unlocked = unlockedCodes(userId);

        List<Badge> newly = new ArrayList<>();
        for (Badge b : Badge.values()) {
            if (unlocked.contains(b.name())) continue;
            if (!b.satisfied(m)) continue;
            UserAchievement ua = new UserAchievement();
            ua.setUserId(userId);
            ua.setBadgeCode(b.name());
            ua.setUnlockedAt(LocalDateTime.now());
            ua.setPointsAwarded(b.rewardPoints);
            achievementMapper.insert(ua);
            newly.add(b);
        }
        if (!newly.isEmpty()) {
            int sum = newly.stream().mapToInt(b -> b.rewardPoints).sum();
            CheckinStat stat = ensureStat(userId);
            stat.setPoints(n(stat.getPoints()) + sum);
            stat.setUpdatedAt(LocalDateTime.now());
            statMapper.updateById(stat);
        }
        return newly;
    }

    /** 评估后返回全目录(含解锁状态)。 */
    @Transactional
    public AchievementListResp list(Long userId) {
        List<Badge> newly = evaluate(userId);
        Map<String, UserAchievement> mine = achievementMapper.selectList(
                        new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserAchievement::getBadgeCode, x -> x, (a, b) -> a));

        List<AchievementResp> badges = new ArrayList<>();
        for (Badge b : Badge.values()) {
            UserAchievement ua = mine.get(b.name());
            badges.add(new AchievementResp(b.name(), b.title, b.icon, b.desc, b.rewardPoints,
                    ua != null, ua == null ? null : ua.getUnlockedAt()));
        }
        List<String> newlyCodes = newly.stream().map(Badge::name).collect(Collectors.toList());
        return new AchievementListResp(mine.size(), Badge.values().length, badges, newlyCodes);
    }

    // ---- 内部 ----

    private Set<String> unlockedCodes(Long userId) {
        return achievementMapper.selectList(new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId))
                .stream().map(UserAchievement::getBadgeCode).collect(Collectors.toSet());
    }

    private CheckinStat ensureStat(Long userId) {
        CheckinStat stat = statMapper.selectById(userId);
        if (stat == null) {
            stat = new CheckinStat();
            stat.setUserId(userId);
            stat.setCurrentStreak(0);
            stat.setMaxStreak(0);
            stat.setTotalDays(0);
            stat.setPoints(0);
            stat.setUpdatedAt(LocalDateTime.now());
            statMapper.insert(stat);
        }
        return stat;
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
```

- [ ] **Step 6: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=AchievementServiceTest test`
Expected: BUILD SUCCESS,四个用例通过。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/studybuddy/achievement/dto backend/src/main/java/com/studybuddy/achievement/AchievementService.java backend/src/test/java/com/studybuddy/achievement/AchievementServiceTest.java
git commit -m "feat: 成就徽章 AchievementService 解锁+发分 + 单测"
```

---

### Task 4: AchievementController(GET /api/achievements)

**Files:**
- Create: `backend/src/main/java/com/studybuddy/achievement/AchievementController.java`

**Interfaces:**
- Consumes: `AchievementService.list(Long)`(Task 3);`CurrentUser.get()`、`R.ok(...)`(现有);`AchievementListResp`。
- Produces: HTTP `GET /api/achievements` → `R<AchievementListResp>`。

- [ ] **Step 1: 创建 Controller**

参照现有 `CheckinController` 的写法(`@RestController` + `R` 包裹 + `CurrentUser.get()`)。创建 `backend/src/main/java/com/studybuddy/achievement/AchievementController.java`:

```java
package com.studybuddy.achievement;

import com.studybuddy.achievement.dto.AchievementListResp;
import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {
    private final AchievementService achievementService;

    @GetMapping
    public R<AchievementListResp> list() {
        return R.ok(achievementService.list(CurrentUser.get()));
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -f backend/pom.xml -B -ntp compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 全量测试**

Run: `mvn -f backend/pom.xml -B -ntp test`
Expected: BUILD SUCCESS,全部测试通过(含原有 + BadgeTest/MetricsCalculatorTest/AchievementServiceTest)。

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/studybuddy/achievement/AchievementController.java
git commit -m "feat: 新增 GET /api/achievements 成就接口"
```

---

### Task 5: Web 端成就页 + 个人页入口

**Files:**
- Modify: `frontend/src/api.js`
- Modify: `frontend/src/router.js`
- Modify: `frontend/src/views/Profile.vue`
- Create: `frontend/src/views/Achievements.vue`

**Interfaces:**
- Consumes: `GET /api/achievements`(Task 4),返回 `{ unlockedCount, totalCount, badges:[{code,title,icon,desc,rewardPoints,unlocked,unlockedAt}], newlyUnlocked:[code] }`;现有 `request`、`toast`。
- Produces: 路由 `/achievements`;`api.js` 导出 `getAchievements()`。

- [ ] **Step 1: api.js 加封装**

在 `frontend/src/api.js` 的"学习目标"区(`getGoal` 之前)插入:

```javascript
// ===== 成就 =====
export function getAchievements() {
  return request('/achievements')
}
```

- [ ] **Step 2: router.js 加路由**

在 `frontend/src/router.js` 的 `routes` 数组中,`couple` 路由之后加一行:

```javascript
  { path: '/couple', component: () => import('./views/Couple.vue') },
  { path: '/achievements', component: () => import('./views/Achievements.vue') }
```

(把原 `couple` 行末尾补上逗号,新增 `achievements` 行。)

- [ ] **Step 3: 创建 Achievements.vue**

创建 `frontend/src/views/Achievements.vue`:

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { getAchievements } from '../api'
import { toast } from '../toast'

const data = ref(null)

async function load() {
  try {
    const d = await getAchievements()
    data.value = d
    if (d.newlyUnlocked && d.newlyUnlocked.length) {
      toast('🎉 解锁 ' + d.newlyUnlocked.length + ' 个新徽章')
    }
  } catch (e) {
    toast(e.message)
  }
}
onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h2 class="page-title">我的成就</h2>
      <p class="page-sub" v-if="data">已解锁 {{ data.unlockedCount }} / {{ data.totalCount }}</p>
    </div>

    <div class="page" style="padding-top: 8px">
      <div v-if="data" class="badge-grid">
        <div
          v-for="b in data.badges"
          :key="b.code"
          :class="['badge', { locked: !b.unlocked }]"
        >
          <div class="badge-ic">{{ b.icon }}</div>
          <div class="badge-title">{{ b.title }}</div>
          <div class="badge-desc">{{ b.unlocked ? ('+' + b.rewardPoints + ' 积分') : b.desc }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.badge-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.badge {
  background: #fff;
  border-radius: 16px;
  padding: 18px 8px;
  text-align: center;
  box-shadow: 0 4px 16px rgba(45, 140, 85, 0.06);
}
.badge.locked { opacity: 0.45; filter: grayscale(1); }
.badge-ic { font-size: 36px; }
.badge-title { font-size: 13px; font-weight: 700; margin-top: 8px; }
.badge-desc { font-size: 11px; color: var(--c-muted); margin-top: 4px; line-height: 1.3; }
</style>
```

- [ ] **Step 4: Profile.vue 加入口**

修改 `frontend/src/views/Profile.vue`。

(a) 把 import 行改为引入 `getAchievements`:

```javascript
import { request, clearAuth, getAchievements } from '../api'
```

(b) 在 `const status = ref(null)` 下加:

```javascript
const ach = ref(null)
```

(c) 把 `load` 函数替换为(追加拉取成就):

```javascript
async function load() {
  try {
    status.value = await request('/checkin/status')
  } catch (e) {
    toast(e.message)
  }
  try {
    ach.value = await getAchievements()
  } catch (e) {
    // 成就拉取失败不影响个人页
  }
}
```

(d) 在模板里 `couple-entry` 按钮**之前**插入成就入口:

```html
      <button class="card couple-entry" @click="router.push('/achievements')">
        <span>🏅 我的成就</span>
        <span class="arrow"><span v-if="ach" style="margin-right:6px">{{ ach.unlockedCount }}/{{ ach.totalCount }}</span>›</span>
      </button>
```

- [ ] **Step 5: 手动验证(若前端可运行)**

浏览器打开个人页:看到"🏅 我的成就 X/Y ›"入口 → 点进成就页 → 徽章网格,已解锁亮色、未解锁灰色;若有新解锁弹 toast。
若本会话不便启动前端,记为"待人工验证",不阻塞提交。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/api.js frontend/src/router.js frontend/src/views/Profile.vue frontend/src/views/Achievements.vue
git commit -m "feat(web): 成就徽章页 + 个人页入口"
```

---

### Task 6: 小程序端成就页 + 个人页入口

**Files:**
- Modify: `miniprogram/src/utils/request.js`
- Modify: `miniprogram/src/pages.json`
- Modify: `miniprogram/src/pages/profile/profile.vue`
- Create: `miniprogram/src/pages/achievements/achievements.vue`

**Interfaces:**
- Consumes: `GET /api/achievements`(Task 4);现有 `request`、`toast`。
- Produces: 页面 `pages/achievements/achievements`;`request.js` 导出 `getAchievements`。

- [ ] **Step 1: request.js 加封装**

在 `miniprogram/src/utils/request.js` 的"业务封装"区(`getGoal` 之前,`getCalendar` 之后)插入:

```javascript
export const getAchievements = () => request('/achievements')
```

- [ ] **Step 2: pages.json 加页面**

在 `miniprogram/src/pages.json` 的 `pages` 数组里,`pages/couple/couple` 之后加一项:

```json
    {
      "path": "pages/couple/couple",
      "style": {
        "navigationBarTitleText": "情侣空间"
      }
    },
    {
      "path": "pages/achievements/achievements",
      "style": {
        "navigationBarTitleText": "我的成就"
      }
    }
```

(给原 couple 项末尾补逗号,新增 achievements 项。)

- [ ] **Step 3: 创建 achievements.vue**

创建 `miniprogram/src/pages/achievements/achievements.vue`:

```vue
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getAchievements, toast } from '../../utils/request'

const data = ref(null)

onShow(() => {
  getAchievements()
    .then((d) => {
      data.value = d
      if (d.newlyUnlocked && d.newlyUnlocked.length) {
        toast('🎉 解锁 ' + d.newlyUnlocked.length + ' 个新徽章')
      }
    })
    .catch((e) => toast(e.message))
})
</script>

<template>
  <view class="page-body">
    <view v-if="data" class="head">已解锁 {{ data.unlockedCount }} / {{ data.totalCount }}</view>
    <view v-if="data" class="grid">
      <view
        v-for="b in data.badges"
        :key="b.code"
        :class="['badge', { locked: !b.unlocked }]"
      >
        <view class="ic">{{ b.icon }}</view>
        <view class="title">{{ b.title }}</view>
        <view class="desc">{{ b.unlocked ? ('+' + b.rewardPoints + ' 积分') : b.desc }}</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.head { font-size: 28rpx; color: var(--c-muted); padding: 24rpx 8rpx; }
.grid { display: flex; flex-wrap: wrap; }
.badge { width: 33.33%; box-sizing: border-box; padding: 28rpx 10rpx; text-align: center; }
.badge.locked { opacity: 0.45; filter: grayscale(1); }
.ic { font-size: 64rpx; }
.title { font-size: 26rpx; font-weight: 700; margin-top: 10rpx; }
.desc { font-size: 22rpx; color: var(--c-muted); margin-top: 6rpx; line-height: 1.3; }
</style>
```

- [ ] **Step 4: profile.vue 加入口**

修改 `miniprogram/src/pages/profile/profile.vue`。

(a) import 行改为:

```javascript
import { getStatus, getAchievements, toast } from '../../utils/request'
```

(b) 在 `const nickname = ...` 行之后加:

```javascript
const ach = ref(null)
```

(c) 把 `onShow(...)` 块替换为(追加拉取成就):

```javascript
onShow(() => {
  getStatus().then((d) => (status.value = d)).catch((e) => toast(e.message))
  getAchievements().then((d) => (ach.value = d)).catch(() => {})
})
```

(d) 加跳转函数(放在 `goCouple` 之后):

```javascript
function goAchievements() {
  uni.navigateTo({ url: '/pages/achievements/achievements' })
}
```

(e) 模板里 `情侣空间` 入口(`@tap="goCouple"` 那个 `view`)**之前**插入:

```html
    <view class="card entry" @tap="goAchievements">
      <text>🏅 我的成就</text>
      <text class="arrow"><text v-if="ach" style="margin-right:8rpx">{{ ach.unlockedCount }}/{{ ach.totalCount }}</text>›</text>
    </view>
```

- [ ] **Step 5: 构建验证(若可运行)**

若小程序可构建,运行项目构建确认无语法错误;开发者工具走查:个人页见"🏅 我的成就 X/Y"入口 → 进成就页 → 徽章网格正常。
若本会话不便构建,记为"待人工验证",不阻塞提交。

- [ ] **Step 6: 提交**

```bash
git add miniprogram/src/utils/request.js miniprogram/src/pages.json miniprogram/src/pages/profile/profile.vue miniprogram/src/pages/achievements/achievements.vue
git commit -m "feat(mp): 成就徽章页 + 个人页入口"
```

---

## Self-Review

**Spec coverage:**
- 独立模块零侵入 → Task 1-4 全在 `com.studybuddy.achievement`,不改 checkin/couple ✓
- 懒解锁 + 幂等 + 发分 → Task 3 evaluate(唯一键 + unlockedCodes 去重) ✓
- 10 个徽章固定目录/分值 → Task 1 Badge 枚举 + BadgeTest 校验数量与顺序 ✓
- 连续类用 maxStreak → Task 1 lambda + Task 2 metrics ✓
- 指标来源(stat/record/couple/commonDays) → Task 2 MetricsCalculator ✓
- GET /api/achievements 返回结构 → Task 3 DTO + Task 4 Controller ✓
- 新表 user_achievement(幂等 schema) → Task 1 schema.sql ✓
- Web 入口+独立页 → Task 5 ✓
- 小程序入口+独立页 → Task 6 ✓
- 测试覆盖(解锁/幂等/无 stat/list 目录) → Task 1/2/3 ✓

**Placeholder scan:** 无 TBD/TODO;手动验证步骤明确标注"可记待人工验证,不阻塞"。

**Type consistency:**
- `MetricsCalculator.compute(Long): AchievementMetrics` — Task 2 定义、Task 3 消费一致。
- `AchievementService.evaluate(Long): List<Badge>` / `list(Long): AchievementListResp` — Task 3 定义、Task 4 消费一致。
- `AchievementListResp(int,int,List<AchievementResp>,List<String>)` 与 Task 3 测试、Task 5/6 前端字段(unlockedCount/totalCount/badges/newlyUnlocked)一致。
- `AchievementResp` 字段(code/title/icon/desc/rewardPoints/unlocked/unlockedAt)与前端模板引用一致。
- `Badge` 字段 `title/icon/desc/rewardPoints` public、`satisfied(...)` 方法 — Task 1 定义,Task 3 引用一致。
- 接口路径 `/api/achievements`(后端)对应前端 `request('/achievements')`(BASE 含 /api)一致。
