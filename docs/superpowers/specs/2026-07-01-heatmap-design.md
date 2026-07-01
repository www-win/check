# 年度打卡热力图 设计文档

- 日期：2026-07-01
- 分支：`feat/heatmap`
- 状态：设计已确认，待写实现计划

## 背景与定位

项目已有**单月网格日历** [`pages/calendar/calendar.vue`](../../../miniprogram/src/pages/calendar/calendar.vue) +
`GET /checkin/calendar?month=yyyy-MM`，负责单月细节展示与补卡。

本功能新增**年度热力图**（GitHub 贡献图风格），定位为**全年概览**，与月历互补：

- 月历：单月细节 + 补卡入口
- 热力图：一屏铺满全年 365/366 天，一眼看出全年坚持程度与断签分布

热力图为**纯展示概览**，不承担补卡等操作（补卡仍在月历页完成）。

## 需求确认（brainstorming 结论）

| 维度 | 决定 |
| --- | --- |
| 定位 | 年度热力图（GitHub 式），独立于月历 |
| 配色 | 简单三态：灰=未签、绿=签到、橙=补卡 |
| 时间范围 | 自然年（默认今年），可左右切换年份 |
| 入口/形态 | 独立新页 `pages/heatmap` + 「我的」页入口（与成就/好友/情侣入口并列） |
| 交互 | 纯展示，格子不可点 |
| 顶部汇总 | 显示：签到天数 · 最长连续 · 补卡天数 · 打卡率 |
| 实现方案 | 方案 1：后端返回稀疏日期数组 + 后端算汇总 |

## 架构

不新建模块，复用 checkin 模块：`CheckinController` / `CheckinService` / `StreakCalculator` / `CheckinRecordMapper`。

### 后端接口

`GET /checkin/heatmap?year=2026`

响应体（`R<HeatmapResp>`）：

```json
{
  "year": 2026,
  "signed": ["2026-01-03", "2026-01-04"],
  "makeup": ["2026-02-10"],
  "summary": {
    "totalDays": 128,
    "longestStreak": 21,
    "makeupDays": 6,
    "rate": 71
  }
}
```

- `signed`：该年 `type=0`（正常签到）的日期，`yyyy-MM-dd`
- `makeup`：该年 `type=1`（补卡）的日期，`yyyy-MM-dd`
- `summary.totalDays`：该年打卡总天数 = signed + makeup
- `summary.longestStreak`：该年**内**最长连续段（补卡日计入"已打卡"）
- `summary.makeupDays`：补卡天数
- `summary.rate`：打卡率，整数百分比

数据查询复用现有 `recordMapper` 的"用户 + 日期范围"模式：
`.eq(userId).ge(checkinDate, year-01-01).le(checkinDate, year-12-31)`（与 `calendar()` 同款）。

### DTO

- `HeatmapResp { int year; List<String> signed; List<String> makeup; HeatmapSummary summary; }`
- `HeatmapSummary { int totalDays; int longestStreak; int makeupDays; int rate; }`

放在 `com.studybuddy.checkin.dto`。

## 核心逻辑（可单测）

在 `StreakCalculator` 中新增两个纯函数，接续既有 `StreakCalculatorTest` 风格：

### `longestStreak(Set<LocalDate> dates)`

把 `signed + makeup` 并成一个集合，扫描找出最长的连续日期段，返回其长度。

- 空集合 → 0
- 单点 → 1
- 全连续 → 段长
- 多段断裂 → 取最长段
- 跨月/跨月末 → 正常连续（按自然日）

### `rate(int totalDays, int year, LocalDate today)`

打卡率 = `round(totalDays / 分母 * 100)`，分母规则：

- `year < today.year`（往年）→ 分母 = 该年全年天数（365/366）
- `year == today.year`（今年）→ 分母 = 年初到 today 的已过天数（含今天）
- `year > today.year`（未来年）→ 返回 0

抽成独立纯方法，便于用**往年**场景做确定性单测（分母固定，避开 `LocalDate.now()` 依赖）。

## 数据流

1. 前端 `pages/heatmap` 在 `onShow` 与切年时调用 `getHeatmap(year)`
2. 后端 `CheckinService.heatmap(userId, year)`：
   - 校验 year 合法
   - 查该年全部 record，按 `type` 分入 signed / makeup
   - 合并成 Set 交给 `StreakCalculator.longestStreak`
   - 组装 summary（totalDays、longestStreak、makeupDays、rate）
   - 返回 `HeatmapResp`
3. 前端拿到稀疏日期数组，按 year 生成空网格并标色，顶部渲染 summary

## 前端页面

新建 `miniprogram/src/pages/heatmap/heatmap.vue`：

- **顶部**：年份切换 `‹ 2026 ›` + 汇总行「签到 X 天 · 最长连续 Y 天 · 补卡 Z 天 · 打卡率 R%」
- **主体**：GitHub 式网格
  - 列 = 周（周一为每列起点，与月历 `(getDay()+6)%7` 一致）× 7 行 = 周几
  - 约 53 列，覆盖全年
  - 每月第一个格子上方标注月份数字
  - 三态配色复用现有 CSS 变量：未签 = 浅灰、签到 = `--c-primary`、补卡 = `--c-accent`
  - 图例：绿=已签到 / 橙=补卡
- **交互**：纯展示不可点；`onShow` 加载，切年重载
- `pages.json` 注册路由
- `utils/request.js` 新增 `getHeatmap(year)`

### 网格布局算法（前端）

- 给定 year，起点 = 该年 1/1 所在周的周一，终点 = 12/31 所在周的周日
- 逐日遍历，每天：列 = 距起点的整周数，行 = `(getDay()+6)%7`
- 用 signed/makeup 两个 Set 判断每格状态（默认未签）

## 入口

`miniprogram/src/pages/profile/profile.vue` 增加一个入口项（与成就/好友/情侣并列），`navigateTo` 到热力图页。

## 错误处理

- year 非法（非数字/超合理范围）→ `BizException(40000, ...)`；前端只传合法年做兜底
- 新用户或该年无打卡 → 返回空数组 + summary 全 0，前端显示空网格（非错误，不弹提示）

## 测试

- `StreakCalculatorTest` 新增 `longestStreak` 用例：空 / 单点 / 全连续 / 多段断裂取最长 / 跨月
- `StreakCalculatorTest` 新增 `rate` 用例：往年满勤=100 / 往年部分 / 今年（构造固定 today）/ 未来年=0
- `CheckinServiceTest` 新增 heatmap 用例（Mockito mock `recordMapper`）：
  - signed / makeup 按 type 正确分类
  - summary 各字段正确
  - rate 用**往年**场景断言（分母确定）
  - 无记录 → 空数组 + summary 全 0

## 非目标（YAGNI）

- 不做心情/连续天数的颜色深浅（三态即可）
- 不做格子点击、tooltip、跳转
- 不做 Web 端（与情侣/好友/私聊一致，暂仅小程序）
- 不改动现有月历页

## 上线影响

- **无新表、无迁移**：仅新增只读查询接口，复用现有 `checkin_record` 表
- 后端重部署即生效
