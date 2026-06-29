# 学伴打卡 · 后端设计文档（方案 C：全功能签到）

- 日期：2026-06-29
- 范围：**后端**（每日签到打卡 + 微信登录 + 前置基建）
- 不在本次范围（各自后续独立 spec）：小程序前端、服务器部署

## 1. 背景与目标

StudyBuddy（学伴）打卡后端，已有 `auth`（手机号+短信验证码登录）、`user` 模块（提交 eab8bbb，当前工作区已删除、仅存于 git）。本次实现「方案 C：全功能每日签到」——全局连续签到，带积分、里程碑奖励、月历、补卡、心情/笔记。

**核心形态**：每日全局签到（不区分事项），统计「当前连续天数 / 历史最长 / 累计天数 / 积分」。

技术栈（沿用现有）：Spring Boot 3.2.5、Java 17、MyBatis-Plus 3.5.7、MySQL、Caffeine、JWT(jjwt 0.12.5)、Lombok，包名 `com.studybuddy`。

## 2. 前置基建（必须先补齐）

现有 `auth` 代码已引用但仓库中尚不存在，需要先落地：

### 2.1 `com.studybuddy.common` 包
- `R<T>`：统一响应体 `{ code:int, msg:String, data:T }`；`R.ok()` / `R.ok(data)` / `R.fail(code,msg)`。
- `BizException(int code, String msg)`：业务异常。
- `GlobalExceptionHandler`（`@RestControllerAdvice`）：捕获 `BizException` → 对应 code/msg；捕获其它异常 → 50000 通用错误；统一返回 `R`。

### 2.2 鉴权拦截器
- `CurrentUser`：基于 `ThreadLocal<Long>` 保存当前请求的 userId，提供 `set/get/clear`。
- `AuthInterceptor`（实现 `HandlerInterceptor`）：从 `Authorization: Bearer <token>` 取 token，调 `JwtUtil.parseUserId` 解析 userId 写入 `CurrentUser`；`afterCompletion` 清理 ThreadLocal。
- `WebMvcConfig`（`WebMvcConfigurer`）：注册拦截器，拦截 `/api/**`，放行 `/api/auth/**`。
- 打卡接口通过 `CurrentUser.get()` 拿当前用户，无需在方法签名传 token。

## 3. 登录注册（微信一键登录）

小程序采用**微信一键登录**；现有短信登录端点保留不动。

### 3.1 流程
1. 小程序 `wx.login()` 拿到临时 `code`，调 `POST /api/auth/wx-login { code }`。
2. 后端用 `appid + secret + code` 请求微信 `https://api.weixin.qq.com/sns/jscode2session`（`grant_type=authorization_code`），换取 `openid`（及 `session_key`、可选 `unionid`）。
3. `userService.findOrCreateByOpenid(openid)`：不存在则建号（隐式注册）。
4. `jwtUtil.generate(userId)` 发 JWT，返回 `LoginResp{ token, userId, nickname }`。

### 3.2 改动点
- `User` 实体新增 `openid`（VARCHAR，唯一），`phone` 改为可空（微信登录用户无手机号）。`user` 表加列 `openid` + 唯一索引。
- `UserService` 新增 `findOrCreateByOpenid(String openid)`。
- 新增 `WxAuthService`：封装 `code2session` 调用（用 Spring `RestClient`），解析微信返回；`errcode != 0` → `BizException(40003, "微信登录失败")`。
- 配置：`studybuddy.wx.appid`、`studybuddy.wx.secret`（`application.yml`，敏感值用环境变量/占位）。
- 新增端点 `POST /api/auth/wx-login`，请求体 `WxLoginReq{ code }`。

## 4. 数据模型

### 4.1 `checkin_record`（签到明细，一天一条）
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO | |
| user_id | BIGINT NOT NULL | |
| checkin_date | DATE NOT NULL | 签到所属日期 |
| type | TINYINT NOT NULL | 0=正常签到 1=补卡 |
| mood | TINYINT NULL | 心情 1–5（可空） |
| note | VARCHAR(200) NULL | 当日笔记（可空） |
| image_url | VARCHAR(500) NULL | 打卡照片 URL（可空） |
| points_earned | INT NOT NULL DEFAULT 0 | 本次获得积分 |
| created_at | DATETIME NOT NULL | 实际写入时间 |

唯一索引 `uk_user_date (user_id, checkin_date)`——防重复签到/补卡的最终兜底。

### 4.2 `checkin_stat`（用户签到统计，一人一条，O(1) 读）
| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT PK | |
| current_streak | INT NOT NULL DEFAULT 0 | 当前连续天数 |
| max_streak | INT NOT NULL DEFAULT 0 | 历史最长连续 |
| total_days | INT NOT NULL DEFAULT 0 | 累计签到天数 |
| last_checkin_date | DATE NULL | 最近一次签到日期 |
| points | INT NOT NULL DEFAULT 0 | 积分余额 |
| updated_at | DATETIME NOT NULL | |

`checkin_stat` 在用户首次签到时按需创建（不存在则插入初始行）。

## 5. 接口

所有接口走鉴权拦截器，用户从 `CurrentUser.get()` 取，统一 `R<T>` 返回。

| 方法 | 路径 | 作用 | 入参 | 返回 data |
|---|---|---|---|---|
| POST | `/api/checkin/upload` | 上传打卡照片 | multipart `file` | `{ url }` |
| POST | `/api/checkin` | 今日签到 | `{ mood?, note?, imageUrl? }` | `{ currentStreak, maxStreak, totalDays, pointsEarned, points, milestone? }` |
| GET | `/api/checkin/status` | 今日状态 | — | `{ todayChecked, currentStreak, maxStreak, totalDays, points }` |
| GET | `/api/checkin/calendar` | 当月日历 | `?month=2026-06` | `{ month, days:[{date, status:0未签/1正常/2补卡, mood?, imageUrl?}] }` |
| POST | `/api/checkin/makeup` | 补卡 | `{ date, mood?, note?, imageUrl? }` | `{ currentStreak, maxStreak, totalDays, points }` |

上传与签到解耦：小程序先调 `upload` 拿到 `url`，再把 `imageUrl` 带进 `checkin`/`makeup`。

DTO：`CheckinReq{mood,note,imageUrl}`、`MakeupReq{date,mood,note,imageUrl}`、`UploadResp{url}`、`CheckinResp`、`CheckinStatusResp`、`CalendarResp`/`CalendarDay`。

## 6. 核心逻辑：连续天数计算（统一重算法）

签到与补卡共用一套连续天数计算，避免特判出错：

> **每次写入（签到或补卡）成功后**：取该用户所有记录中的最大日期为锚 `anchor = last_checkin_date`；从 `anchor` 往回逐日检查 `checkin_record` 是否存在该日记录，连续命中的天数即 `current_streak`，回看窗口上限 **90 天**。`max_streak = max(旧 max_streak, current_streak)`。

实现：一次性查出 `[anchor-90, anchor]` 区间内该用户的签到日期集合，在内存中从 anchor 往回数连续。

举例：
- 周一签 → 断 → 周三签：`current_streak=1`（周三往回周二无记录）。
- 接着补卡周二：周一二三齐全 → 从周三往回连续 3 → `current_streak=3`，自动接续，无需特判。

数据量小（每次回扫 ≤90 行），换取逻辑简单可靠。

## 7. 积分 / 补卡规则（配置化 `CheckinProps`）

放 `application.yml`，`@ConfigurationProperties(prefix="studybuddy.checkin")`：

- **签到积分**：正常签到 `+10`。
- **里程碑奖励**：写入后 `current_streak` 命中 `{7,30,100}` 额外 `+{20,100,300}`（map 配置，可调）；返回 `milestone` 标识本次是否触发。
- **补卡**：
  - 消耗 `50` 积分。
  - 仅可补**过去 7 天内、且尚未签到**的日期（不含今天、不含未来）。
  - 校验失败 → `BizException`：积分不足 `40010`、超出可补窗口 `40011`、该日已签 `40012`。
  - **补卡不发签到积分**（防刷分），仅扣分并恢复连续。

> 待确认数值：签到 +10、里程碑 7/30/100→+20/100/300、补卡花 50 分、可补窗口 7 天。

## 8. 打卡照片（OSS 图片存储）

照片存对象存储（阿里云 OSS / 腾讯云 COS）；后端**代理上传**（小程序 → 后端 → OSS），实现简单、不暴露密钥。

- **抽象**：`ImageStorage` 接口 `String upload(byte[] bytes, String ext)` → 返回可访问 URL。默认实现 `OssImageStorage`（阿里云 OSS SDK）；provider 可换，便于后续切 COS。
- **上传端点** `POST /api/checkin/upload`（`multipart/form-data`，字段 `file`）：
  - 校验类型 `jpg/jpeg/png/webp`、大小 ≤ `5MB`，否则 `BizException`（类型 `40020`、超限 `40021`）。
  - 生成对象键 `checkin/{userId}/{yyyyMM}/{uuid}.{ext}`，上传后返回 `{ url }`。
- **签到/补卡**：仅保存 `imageUrl` 字符串（不在签到事务里碰 OSS）。
- **配置** `studybuddy.oss.*`（`application.yml`，密钥用环境变量）：`endpoint`、`bucket`、`accessKeyId`、`accessKeySecret`、`urlPrefix`（公网访问前缀/CDN 域名）。
- **依赖**：`pom.xml` 增加 `aliyun-sdk-oss`。

## 9. 一致性 / 并发 / 缓存

- **事务**：签到/补卡 = 写明细 + upsert 统计，置于同一 `@Transactional`。
- **防重复**：先查今日是否已签；并发兜底靠唯一索引 `uk_user_date`，捕获 `DuplicateKeyException` → `BizException(40012, "今天已签到")`。
- **缓存**：Caffeine 缓存 `status` 结果（key=userId，TTL 60s），签到/补卡后主动失效该 key。
- **时间**：以服务器时区 `LocalDate.now()` 为「今天」。

## 10. 模块结构

```
com.studybuddy
├── common/            R, BizException, GlobalExceptionHandler, CurrentUser
├── config/            WebMvcConfig, AuthInterceptor
├── auth/              (新增) WxAuthService, dto/WxLoginReq；AuthController 加 wx-login
├── user/              User 加 openid；UserService 加 findOrCreateByOpenid
└── checkin/
    ├── CheckinController
    ├── CheckinService
    ├── StreakCalculator          连续天数重算（可独立测试）
    ├── CheckinProps              积分/补卡配置
    ├── storage/                  ImageStorage 接口, OssImageStorage 实现, OssProps
    ├── entity/                   CheckinRecord, CheckinStat
    ├── mapper/                   CheckinRecordMapper, CheckinStatMapper
    └── dto/                      CheckinReq, MakeupReq, UploadResp, CheckinResp, CheckinStatusResp, CalendarResp
```

## 11. 测试（Service 层为主）

- `StreakCalculator`：断连/连续/补卡接续/跨窗口边界等多场景。
- 签到：首签建统计行、重复签到拒绝、积分发放、里程碑触发。
- 补卡：窗口校验、积分不足、目标日已签、补卡接续连续。
- 上传：类型/大小校验拒绝；`OssImageStorage` 用 mock 验证对象键生成与 URL 拼接。
- `WxAuthService`：微信返回 errcode != 0 时报错（mock RestClient）。

## 12. 后续独立子项目（不在本 spec）

1. **小程序前端**：微信小程序，签到页 / 日历 / 补卡 / 个人积分；调用本后端接口。
2. **服务器部署**：MySQL、应用打包（jar/Docker）、Nginx、HTTPS 域名（微信小程序要求 https）、对象存储开通与配置，环境变量（appid/secret、jwt secret、oss accessKey/bucket 等）。
