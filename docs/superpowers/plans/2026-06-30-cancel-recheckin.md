# 撤销今日打卡 + 重打 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 允许用户撤销今天的正常打卡(删除记录、退回积分、重算连续/累计天数),然后在同一页面重新打卡。

**Architecture:** 后端在 `CheckinService` 新增 `cancelToday(userId)` 事务方法 + `CheckinController` 新增 `POST /api/checkin/cancel`,复用现有 `recompute`/`ensureStat`/`findRecord` 私有方法。前端(Web Vue + 小程序 uni-app)在"今日已完成打卡"区块加"撤销重打"按钮,确认后调撤销接口并刷新状态,页面自动回到选打卡方式。

**Tech Stack:** Spring Boot + MyBatis-Plus + JUnit5/Mockito(后端);Vue 3(Web);uni-app/Vue 3(小程序)。

## Global Constraints

- 仅可撤销**今天**且 `type==0`(正常打卡)的记录;补卡(`type==1`)、历史日期不可撤销。
- 撤销为整体 `@Transactional`。
- `maxStreak`(历史最高连续)撤销后不回退。
- 退积分用 `pointsEarned`(已含里程碑奖励),`points` 不得低于 0。
- 无数据库结构变更,无需迁移脚本。
- 错误码:`40013`=今天还没打卡;`40014`=补卡记录不支持撤销。
- 后端构建/测试从仓库根执行;若 PATH 未带工具链,先注入:
  `$env:JAVA_HOME="$env:USERPROFILE\tools\jdk-17.0.19+10"; $env:Path="$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.9\bin;$env:Path"`

---

### Task 1: 后端撤销逻辑 `CheckinService.cancelToday` + 单元测试

**Files:**
- Modify: `backend/src/main/java/com/studybuddy/checkin/CheckinService.java`
- Test: `backend/src/test/java/com/studybuddy/checkin/CheckinServiceTest.java`

**Interfaces:**
- Consumes: 现有私有方法 `findRecord(Long, LocalDate)`、`ensureStat(Long)`、`recompute(Long, CheckinStat)`、`n(Integer)`;mapper `recordMapper.deleteById(Serializable)`、`statMapper.updateById(...)`;DTO `CheckinStatusResp(boolean, int, int, int, int)`;实体 `CheckinRecord`(字段 `id/type/pointsEarned`)、`CheckinStat`(字段 `points/currentStreak/maxStreak/totalDays/updatedAt`)。
- Produces: `public CheckinStatusResp cancelToday(Long userId)` —— 供 Task 2 的 Controller 调用。

- [ ] **Step 1: 写失败测试**

在 `CheckinServiceTest.java` 末尾(最后一个 `}` 之前)追加三个用例。注意现有 import 已含 `LocalDate`、`assertEquals`、`assertThrows`、`any`、`when`、`lenient`,无需新增 import。

```java
    @Test
    void cancelTodayRefundsPointsAndDeletesRecord() {
        CheckinRecord today = new CheckinRecord();
        today.setId(99L);
        today.setUserId(uid);
        today.setCheckinDate(LocalDate.now());
        today.setType(0);
        today.setPointsEarned(10);
        when(recordMapper.selectOne(any())).thenReturn(today);

        CheckinStat stat = new CheckinStat();
        stat.setUserId(uid);
        stat.setPoints(30);
        stat.setCurrentStreak(3);
        stat.setMaxStreak(5);
        stat.setTotalDays(3);
        when(statMapper.selectById(uid)).thenReturn(stat);
        // recompute 内部查询：无剩余记录 → 连续/累计归零
        when(recordMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(recordMapper.selectCount(any())).thenReturn(0L);

        CheckinStatusResp resp = service.cancelToday(uid);

        org.mockito.Mockito.verify(recordMapper).deleteById(99L);
        assertEquals(false, resp.isTodayChecked());
        assertEquals(20, resp.getPoints());   // 30 - 10
        assertEquals(5, resp.getMaxStreak()); // 历史最高不回退
        assertEquals(0, resp.getTotalDays());
        assertEquals(0, resp.getCurrentStreak());
    }

    @Test
    void cancelTodayWhenNotCheckedThrows40013() {
        when(recordMapper.selectOne(any())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.cancelToday(uid));
        assertEquals(40013, e.getCode());
    }

    @Test
    void cancelTodayMakeupRecordThrows40014() {
        CheckinRecord today = new CheckinRecord();
        today.setId(99L);
        today.setCheckinDate(LocalDate.now());
        today.setType(1); // 补卡
        when(recordMapper.selectOne(any())).thenReturn(today);

        BizException e = assertThrows(BizException.class, () -> service.cancelToday(uid));
        assertEquals(40014, e.getCode());
    }
```

需要为测试引入 `CheckinStatusResp` 的 import。在文件顶部已有 import 区追加:

```java
import com.studybuddy.checkin.dto.CheckinStatusResp;
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=CheckinServiceTest test`
Expected: 编译失败或测试失败,提示 `cancelToday` 方法不存在。

- [ ] **Step 3: 实现 `cancelToday`**

在 `CheckinService.java` 中,`makeup(...)` 方法之后、`// ---- 内部 ----` 注释之前,插入:

```java
    /** 撤销今日的正常打卡:删除记录、退回积分、重算连续/累计天数。 */
    @Transactional
    public CheckinStatusResp cancelToday(Long userId) {
        LocalDate today = LocalDate.now();
        CheckinRecord record = findRecord(userId, today);
        if (record == null) {
            throw new BizException(40013, "今天还没打卡");
        }
        if (record.getType() != null && record.getType() == 1) {
            throw new BizException(40014, "补卡记录不支持撤销");
        }

        CheckinStat stat = ensureStat(userId);
        int refunded = n(record.getPointsEarned());
        stat.setPoints(Math.max(0, n(stat.getPoints()) - refunded));

        recordMapper.deleteById(record.getId());

        recompute(userId, stat);
        stat.setUpdatedAt(LocalDateTime.now());
        statMapper.updateById(stat);

        return new CheckinStatusResp(false, stat.getCurrentStreak(),
                stat.getMaxStreak(), stat.getTotalDays(), stat.getPoints());
    }
```

- [ ] **Step 4: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=CheckinServiceTest test`
Expected: BUILD SUCCESS,`CheckinServiceTest` 全部通过(含原有 5 个 + 新增 3 个)。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/studybuddy/checkin/CheckinService.java backend/src/test/java/com/studybuddy/checkin/CheckinServiceTest.java
git commit -m "feat: 撤销今日打卡 cancelToday + 单测"
```

---

### Task 2: 后端撤销接口 `POST /api/checkin/cancel`

**Files:**
- Modify: `backend/src/main/java/com/studybuddy/checkin/CheckinController.java`

**Interfaces:**
- Consumes: `checkinService.cancelToday(Long)`(Task 1);`CurrentUser.get()`;`R.ok(...)`;`CheckinStatusResp`。
- Produces: HTTP 端点 `POST /api/checkin/cancel` 返回 `R<CheckinStatusResp>` —— 供 Task 3、4 前端调用。

- [ ] **Step 1: 新增 Controller 方法**

在 `CheckinController.java` 中,`status()` 方法之后插入:

```java
    @PostMapping("/cancel")
    public R<CheckinStatusResp> cancel() {
        return R.ok(checkinService.cancelToday(CurrentUser.get()));
    }
```

`CheckinStatusResp`、`PostMapping`、`R`、`CurrentUser` 的 import 文件中已存在,无需新增。

- [ ] **Step 2: 编译验证**

Run: `mvn -f backend/pom.xml -B -ntp compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 全量测试**

Run: `mvn -f backend/pom.xml -B -ntp test`
Expected: BUILD SUCCESS,所有测试通过。

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/studybuddy/checkin/CheckinController.java
git commit -m "feat: 新增 POST /api/checkin/cancel 撤销今日打卡接口"
```

---

### Task 3: Web 端撤销重打按钮

**Files:**
- Modify: `frontend/src/views/Today.vue`

**Interfaces:**
- Consumes: `POST /api/checkin/cancel`(Task 2);`request(path, {method})`、`toast`(已 import);组件内 `load()`、`status`、`submitting`。
- Produces: 无(终端 UI)。

- [ ] **Step 1: 新增 `cancelCheckin` 方法**

在 `Today.vue` 的 `<script setup>` 中,`doCheckin` 函数之后插入:

```javascript
async function cancelCheckin() {
  if (!window.confirm('撤销后本次所得积分将退回,确定吗?')) return
  submitting.value = true
  try {
    await request('/checkin/cancel', { method: 'POST' })
    toast('已撤销,可以重新打卡')
    await load()
  } catch (e) {
    toast(e.message)
  } finally {
    submitting.value = false
  }
}
```

- [ ] **Step 2: 在已打卡区块加按钮**

把模板中的 `checked-banner` 块替换为(新增最后一行按钮):

```html
      <div v-if="status && status.todayChecked" class="checked-banner">
        <div class="cb-ic">✓</div>
        <div class="cb-main">今日已完成打卡</div>
        <div class="cb-sub">明天再来,保持连续 🔥</div>
        <button class="cb-redo" type="button" :disabled="submitting" @click="cancelCheckin">打错了?撤销重打</button>
      </div>
```

- [ ] **Step 3: 加按钮样式**

`Today.vue` 当前无 `<style>` 块(样式来自全局)。在文件末尾 `</template>` 之后追加一个 scoped 样式块:

```html
<style scoped>
.cb-redo {
  margin-top: 14px;
  background: rgba(255, 255, 255, 0.85);
  color: #2D8C55;
  border: none;
  border-radius: 20px;
  padding: 8px 22px;
  font-size: 14px;
  font-weight: 600;
}
.cb-redo:disabled { opacity: 0.6; }
</style>
```

- [ ] **Step 4: 手动验证(若前端可运行)**

在浏览器打开 Web 端今日页:已打卡状态下点击"打错了?撤销重打"→ 确认 → 提示"已撤销" → 页面回到"选择打卡方式" → 积分数减少。
若本会话不便启动前端,记录此步为"待人工验证",不阻塞提交。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/Today.vue
git commit -m "feat(web): 今日打卡页加撤销重打按钮"
```

---

### Task 4: 小程序端撤销重打按钮

**Files:**
- Modify: `miniprogram/src/pages/today/today.vue`

**Interfaces:**
- Consumes: `POST /api/checkin/cancel`(Task 2);`request(path, {method})`、`toast`(已 import);组件内 `loadStatus()`、`status`、`submitting`;`uni.showModal`。
- Produces: 无(终端 UI)。

- [ ] **Step 1: 新增 `cancelCheckin` 方法**

在 `today.vue` 的 `<script setup>` 中,`doCheckin` 函数之后插入:

```javascript
function cancelCheckin() {
  uni.showModal({
    title: '撤销打卡',
    content: '撤销后本次所得积分将退回,确定吗?',
    success: (r) => {
      if (!r.confirm) return
      submitting.value = true
      request('/checkin/cancel', { method: 'POST' })
        .then(() => {
          toast('已撤销,可以重新打卡')
          loadStatus()
        })
        .catch((e) => toast(e.message))
        .finally(() => (submitting.value = false))
    }
  })
}
```

- [ ] **Step 2: 在已打卡区块加按钮**

把模板中的 `checked` 块替换为(新增最后一行按钮):

```html
    <view v-if="status && status.todayChecked" class="checked">
      <view class="checked-ic">✓</view>
      <view class="checked-main">今日已完成打卡</view>
      <view class="checked-sub">明天再来，保持连续 🔥</view>
      <button class="checked-redo" hover-class="btn-hover" :disabled="submitting" @tap="cancelCheckin">打错了？撤销重打</button>
    </view>
```

- [ ] **Step 3: 加按钮样式**

在 `today.vue` 的 `<style scoped>` 中,`.checked-sub { ... }` 规则之后插入:

```css
.checked-redo { margin-top: 28rpx; background: rgba(255,255,255,.9); color: var(--c-primary-d); font-size: 26rpx; font-weight: 700; border-radius: 40rpx; padding: 0 36rpx; height: 72rpx; line-height: 72rpx; }
```

- [ ] **Step 4: 手动验证(若可运行)**

小程序今日页已打卡状态下点击"打错了?撤销重打"→ 弹窗确认 → 提示"已撤销" → 页面回到"选择打卡方式" → 积分数减少。
若本会话不便启动小程序,记录此步为"待人工验证",不阻塞提交。

- [ ] **Step 5: 提交**

```bash
git add miniprogram/src/pages/today/today.vue
git commit -m "feat(mp): 今日打卡页加撤销重打按钮"
```

---

## Self-Review

**Spec coverage:**
- 后端 `POST /api/checkin/cancel` + `cancelToday` → Task 1 + 2 ✓
- 仅今天正常打卡、补卡报错(40014)、未打卡报错(40013) → Task 1 测试覆盖 ✓
- 退积分(含里程碑)、删除记录、recompute、maxStreak 不回退 → Task 1 实现 ✓
- Web 撤销按钮 → Task 3 ✓
- 小程序撤销按钮 → Task 4 ✓
- 测试三用例 → Task 1 ✓
- 无迁移脚本 → 确认无 DB 变更 ✓

**Placeholder scan:** 无 TBD/TODO;手动验证步骤明确标注"可记为待人工验证,不阻塞"。

**Type consistency:** `cancelToday(Long): CheckinStatusResp` 在 Task 1 定义、Task 2 调用一致;`CheckinStatusResp` 构造器签名 `(boolean, int, int, int, int)` 与 DTO 一致;`request('/checkin/cancel', { method: 'POST' })` 两端工具签名一致(Web 用 `body` 但本接口无 body;小程序用 `data`,同样无 body)。
