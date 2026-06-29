# 学伴打卡 · 网页前端设计文档(Vue3 + Vite,清新自然绿)

- 日期：2026-06-29
- 范围：**网页前端**,对接已运行的后端(5 个接口),覆盖已有功能
- 后续：以同一套设计/结构转微信小程序(独立 spec)

## 1. 目标与约束

为已实现的打卡后端做一个**好看、可用的网页前端**,移动优先,结构对齐小程序以便后续平移。

- 技术栈：Vue 3 + Vite + Vue Router；原生 `fetch`,不引重型状态库。
- 视觉：清新自然绿(成长/学习感)。
- 跨域：Vite dev server 代理 `/api` → `http://127.0.0.1:8080`,**开发期不改后端**。
- 后端不变更：本 spec 不动后端代码。

## 2. 整体形态

移动优先:页面中央一个手机宽度容器(约 390px,移动端全宽),底部 Tab 切换。

```
┌──────────────┐
│   顶部标题栏    │
│   页面内容     │
├──────────────┤
│ 今日 │ 日历 │ 我的 │
└──────────────┘
```

界面:**登录页**(无 Tab)+ 三个 Tab 页(**今日 / 日历 / 我的**)。

## 3. 页面与接口映射

### 3.1 登录页 `/login`
- 输入手机号 → 「获取验证码」→ `POST /api/auth/send-code {phone}`。
- 输入验证码 → 「登录」→ `POST /api/auth/login {phone,code}` → 存 `token`、`userId`、`nickname` 到 `localStorage`,跳「今日」。
- 开发期提示：短信为假,**验证码打印在后端控制台**。
- 手机号格式前端校验(11 位数字);后端错误码 40001/40002 → toast。

### 3.2 今日页 `/today`(主界面)
- 顶部统计卡：🔥 当前连续天数、累计天数、积分(`GET /api/checkin/status`)。
- 核心大圆按钮：未签=「今日打卡」,已签=「✓ 今日已打卡」(禁用)。
- 打卡面板:心情选择(5 个表情 1–5)、一行笔记(≤200 字)、可选上传照片。
- 提交 → `POST /api/checkin {mood?,note?,imageUrl?}` → 成功后刷新统计;若触发里程碑(`milestone` 非空)弹祝贺。

### 3.3 日历页 `/calendar`
- 当月月历(`GET /api/checkin/calendar?month=yyyy-MM`)。
- 格子状态:0 未签(灰)/1 签到(绿点)/2 补卡(橙点);当天高亮;有 mood 显示表情。
- 点**过去且未签**的日子 → 补卡确认弹窗 → `POST /api/checkin/makeup {date}`;成功刷新;积分不足(40010)/超窗口(40011)→ toast。
- 月份左右切换。

### 3.4 我的页 `/profile`
- 昵称(localStorage)、最长连续、累计天数、积分(来自 status)。
- 退出登录:清 localStorage,跳登录。

## 4. 图片上传

- 「上传照片」→ `POST /api/checkin/upload`(multipart `file`)→ 返回 `{url}` 填入 `imageUrl`。
- OSS 未配置时后端返回 50001 → **优雅降级**:toast「图片功能需配置 OSS,可先不带照片打卡」,签到流程照常(照片可选)。
- 前端限制:类型 jpg/png/webp、大小 ≤5MB(与后端一致)。

## 5. 技术结构

```
frontend/
  index.html
  package.json
  vite.config.js          # server.proxy: '/api' -> http://127.0.0.1:8080
  src/
    main.js               # 创建 app + router 挂载
    App.vue               # 手机容器外壳 + <router-view> + TabBar
    api.js                # request(path,opts):自动带 Bearer token;401/40100 → 跳登录
    router.js             # /login /today /calendar /profile;路由守卫:无 token → /login
    theme.css             # 设计令牌:主绿 #3DBA6F 等,圆角、阴影、间距
    views/
      Login.vue
      Today.vue
      Calendar.vue
      Profile.vue
    components/
      TabBar.vue          # 底部三 Tab
      MoodPicker.vue      # 5 表情选择
      StatCard.vue        # 连续/累计/积分 统计卡
      Toast.vue           # 轻提示(或函数式 toast)
```

### 数据流
- `api.js` 统一封装:读 `localStorage.token` 加 `Authorization: Bearer`;响应 `code!==0` 抛错带 msg;`code===40100` 清 token 并跳 `/login`。
- 各 view 在 `onMounted` 拉数据;打卡/补卡后重新拉 status / calendar。

### 视觉令牌(theme.css)
- 主色 `--c-primary:#3DBA6F`,深 `--c-primary-d:#2E9E5B`,浅底 `--c-bg:#F4FBF6`,文字 `--c-text:#1F2A24`,次要 `--c-muted:#8A9A90`。
- 圆角 `--r:16px`,卡片阴影柔和,按钮主绿、按下加深。

## 6. 错误码对应提示

| code | 前端提示 |
|---|---|
| 40001/40002 | 手机号格式错 / 验证码错误或过期 |
| 40010 | 积分不足,无法补卡 |
| 40011 | 该日期不可补卡 |
| 40012 | 今天/该日已打卡 |
| 50001 | 图片功能需配置 OSS |
| 40100 | 登录失效,请重新登录(自动跳登录) |

## 7. 验证(对接真实后端,手动)

启动后端(8080)+ Vite dev(默认 5173),走通:登录 → 今日打卡(带心情/笔记)→ 看统计变化 → 日历查看绿点 → 补卡(积分够时)→ 退出登录。前端不写重单元测试。

## 8. 不在本 spec

- 微信小程序版本(后续独立 spec,沿用本设计与视觉)。
- 后端任何改动(含正式短信、OSS、CORS 生产配置)。
