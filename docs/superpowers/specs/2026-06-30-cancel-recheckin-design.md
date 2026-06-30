# 撤销今日打卡 + 重打 设计

日期:2026-06-30

## 背景与目标

用户打卡后发现打错了(选错心情、传错图、误触普通打卡等),希望能撤销今天的打卡,然后重新打一次。

本次只实现"撤销 + 重打"(真正把当次记录删除、状态回退),不做"仅编辑内容"。

## 范围

- 只允许撤销**今天**的**正常打卡**(`checkin_record.type == 0`)。
- 不支持撤销补卡(`type == 1`)。
- 不支持撤销历史日期。
- 撤销后用户在同一页面重新选打卡方式,走现有 `/api/checkin` 接口重打,无需新增"重打"接口。

## 后端设计

### 接口

`POST /api/checkin/cancel`

- 入参:无(取当前登录用户)。
- 返回:`CheckinStatusResp`(撤销后此时 `todayChecked=false`)。

### CheckinController

新增方法:

```java
@PostMapping("/cancel")
public R<CheckinStatusResp> cancel() {
    return R.ok(checkinService.cancelToday(CurrentUser.get()));
}
```

### CheckinService.cancelToday(Long userId)

整体 `@Transactional`,步骤:

1. 查今天的记录(`findRecord(userId, LocalDate.now())`)。为空 → 抛 `BizException(40013, "今天还没打卡")`。
2. 若 `record.getType() != null && record.getType() == 1`(补卡)→ 抛 `BizException(40014, "补卡记录不支持撤销")`。
3. 取 `stat = ensureStat(userId)`。退积分:`stat.setPoints(max(0, n(stat.getPoints()) - n(record.getPointsEarned())))`。
   - `pointsEarned` 已包含基础分 + 里程碑奖励,直接整体退回即可。
4. `recordMapper.deleteById(record.getId())` 删除该条记录。
5. 调用现有 `recompute(userId, stat)` 重算 `currentStreak / totalDays / lastCheckinDate`。
6. `maxStreak` 不回退(历史最高保留)。`recompute` 内部用 `Math.max(已有, 新值)`,天然保留。
7. `stat.setUpdatedAt(now)`,`statMapper.updateById(stat)`。
8. 返回 `new CheckinStatusResp(false, currentStreak, maxStreak, totalDays, points)`。

### 错误码

| 码 | 含义 |
|----|------|
| 40013 | 今天还没打卡(无可撤销记录) |
| 40014 | 补卡记录不支持撤销 |

### 对其他模块的影响

- 情侣空间共同统计实时读取 `checkin_stat`,撤销后自动反映,无需额外处理。
- 无数据库结构变更(仅删除一行 `checkin_record` + 更新 `checkin_stat`),无需迁移脚本。

## 前端设计(两端共用同一接口)

在"今日已完成打卡"区块下方加一个文字按钮:**"打错了?撤销重打"**。

点击 → 二次确认("撤销后本次所得积分将退回,确定吗?")→ 调 `POST /checkin/cancel` → 重新加载状态。状态变为 `todayChecked=false` 后,页面自动回到"选择打卡方式",用户可直接重打。

### Web(frontend/src/views/Today.vue)

- 新增 `cancelCheckin()`:`window.confirm` 确认 → `request('/checkin/cancel', { method: 'POST' })` → `toast` → `await load()`。
- 在 `checked-banner` 内加按钮绑定 `cancelCheckin`。

### 小程序(miniprogram/src/pages/today/today.vue)

- 新增 `cancelCheckin()`:`uni.showModal` 确认 → `request('/checkin/cancel', { method: 'POST' })` → `toast` → `loadStatus()`。
- 在 `checked` 区块内加按钮绑定 `@tap="cancelCheckin"`。

## 测试

`CheckinServiceTest` 新增用例:

1. 正常撤销:先打卡再撤销 → 今日记录被删除、`points` 退回到打卡前、`currentStreak/totalDays` 重算正确、`status().todayChecked == false`。
2. 未打卡时撤销 → 抛 `40013`。
3. 补卡记录撤销 → 先补一条过去的卡(或构造 type==1 的今日记录)→ 抛 `40014`。

## 非目标(YAGNI)

- 不做"仅编辑打卡内容"。
- 不支持撤销历史/补卡记录。
- 不加撤销次数限制、撤销冷却等(如有需要后续再议)。
