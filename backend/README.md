# 学伴打卡 后端

Spring Boot 3.2.5 + Java 17 + MyBatis-Plus + MySQL + JWT，实现「全功能每日签到」：微信登录、签到、连续/积分/里程碑、月历、补卡、拍照打卡（OSS）。

设计文档见 [../docs/superpowers/specs/2026-06-29-checkin-design.md](../docs/superpowers/specs/2026-06-29-checkin-design.md)。

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8

## 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS studybuddy DEFAULT CHARSET utf8mb4;
```
```bash
mysql -u root -p studybuddy < src/main/resources/db/schema.sql
```

## 配置（环境变量，见 application.yml）

| 变量 | 说明 |
|---|---|
| `DB_USER` / `DB_PASSWORD` | MySQL 账号密码 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节） |
| `WX_APPID` / `WX_SECRET` | 微信小程序 AppID / AppSecret |
| `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_AK` / `OSS_SK` / `OSS_URL_PREFIX` | 阿里云 OSS（拍照打卡需要） |

## 构建与运行

```bash
mvn clean package          # 编译 + 跑测试
mvn spring-boot:run        # 本地启动（默认 8080）
java -jar target/studybuddy-backend-0.1.0.jar
```

## 接口一览

鉴权：除 `/api/auth/**` 外，其余 `/api/**` 需带 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/wx-login` | 微信一键登录，`{code}` → `{token,userId,nickname}` |
| POST | `/api/auth/send-code` | 发短信验证码（开发环境打印在日志） |
| POST | `/api/auth/login` | 手机号+验证码登录 |
| POST | `/api/checkin/upload` | 上传打卡照片（multipart `file`）→ `{url}` |
| POST | `/api/checkin` | 今日签到 `{mood?,note?,imageUrl?}` |
| GET | `/api/checkin/status` | 今日状态 |
| GET | `/api/checkin/calendar?month=2026-06` | 当月日历 |
| POST | `/api/checkin/makeup` | 补卡 `{date,mood?,note?,imageUrl?}` |

统一响应：`{ "code":0, "msg":"ok", "data":... }`，`code!=0` 为业务错误。

## 错误码

| code | 含义 |
|---|---|
| 40010 | 积分不足，无法补卡 |
| 40011 | 补卡日期非法 / 超出窗口 |
| 40012 | 当日已签到 |
| 40020 / 40021 | 上传文件类型不支持 / 超过大小限制 |
| 40100 | 未登录 / 登录失效 |
| 50000 | 服务器内部错误 |
