# 学习目标功能设计文档(文字目标 + 倒计时)

- 日期：2026-06-29
- 范围：后端新增 `goal` 模块 + 前端「目标卡 + 编辑弹层」
- 形态：每个用户一个**当前学习目标**(一段文字 + 可选目标日期),仅作激励展示,不自动算进度。

## 1. 背景

学伴打卡已有签到/积分/日历。本功能让用户写下自己的学习目标(如「每天背 50 个单词」「考雅思 7 分」)并可设目标日期做倒计时,展示在今日页顶部。

## 2. 后端(com.studybuddy.goal)

### 2.1 表 `study_goal`(一人一条)
| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT PK | 主键即用户(INPUT,非自增) |
| content | VARCHAR(200) NOT NULL | 目标文字 |
| target_date | DATE NULL | 目标日期,可空 |
| updated_at | DATETIME NOT NULL | |

### 2.2 接口(`/api/goal`,走现有 AuthInterceptor,用户取自 CurrentUser)
| 方法 | 路径 | 入参 | 返回 data |
|---|---|---|---|
| GET | `/api/goal` | — | `{content, targetDate}` 或 `null`(未设定) |
| PUT | `/api/goal` | `{content, targetDate?}` | `{content, targetDate}` |
| DELETE | `/api/goal` | — | `null` |

- `PUT` 为 upsert:无则插入,有则更新;`updated_at` 刷新。
- 校验:`content` 必填、`@Size<=200`;`targetDate` 可空,格式 `yyyy-MM-dd`(`@JsonFormat`)。校验失败 → 40000。

### 2.3 类结构
```
com.studybuddy.goal
├── GoalController       GET/PUT/DELETE
├── GoalService         get(userId) / upsert(userId,req) / clear(userId)
├── entity/StudyGoal
├── mapper/StudyGoalMapper   (BaseMapper, @Mapper)
└── dto/  GoalReq{content,targetDate}  GoalResp{content,targetDate}
```

### 2.4 数据库
`db/schema.sql` 增加 `study_goal` 建表语句(本地已运行的库需手动补执行该段)。

## 3. 前端(Vue)

### 3.1 今日页目标卡(Today.vue 顶部)
- 已设定:显示目标文字 +(有 `targetDate` 时)倒计时徽标,右上角「编辑」按钮。
- 未设定:显示引导「+ 设定你的学习目标」,点开编辑弹层。
- 进入今日页时 `GET /api/goal` 拉取。

### 3.2 组件
- `GoalCard.vue`:props `goal`(可空);emit `edit`。负责展示文字 + 倒计时。
- `GoalEditor.vue`:弹层(遮罩 + 卡片)。字段:目标文字(textarea,≤200)、目标日期(`<input type="date">`,可空)。按钮:保存(`PUT`)、清除目标(`DELETE`,仅已存在时显示)、取消。emit `saved` / `close`。

### 3.3 倒计时文案(前端计算)
`diff = targetDate − today`(按天):
- `diff > 0` → `🎯 距目标还有 {diff} 天`
- `diff === 0` → `🎯 今天就是目标日`
- `diff < 0` → `已超 {-diff} 天,继续加油`
- 无 `targetDate` → 不显示倒计时,仅文字。

### 3.4 api 封装
复用 `request`:`getGoal()`=GET、`saveGoal(body)`=PUT、`clearGoal()`=DELETE。

## 4. 样式
复用清新自然绿令牌。目标卡用浅绿底卡片;倒计时徽标用主绿/橙强调。弹层遮罩半透明,卡片圆角。

## 5. 验证(手动,对接真实后端)
登录 → 今日页点「设定目标」→ 填文字+日期保存 → 卡片显示目标与倒计时 → 编辑修改 → 清除 → 回到引导态。后端三接口经 Vite 代理走通。

## 6. 不在本 spec
- 目标进度自动统计、多目标清单、目标达成奖励(后续可迭代)。
