# 微信云托管 部署指南（后端上线，让小程序真机可用）

> 目标：把 Spring Boot 后端部署到微信云托管，拿到免备案 HTTPS 域名，供小程序调用。

## 0. 前提
- 已注册小程序，AppID = `wxf6d8c62841394092`，已有 AppSecret。
- 代码已在 GitHub：`https://github.com/www-win/check`（云托管可直接拉代码构建）。
- 仓库已含：`backend/Dockerfile`、`backend/.dockerignore`、`backend/settings-docker.xml`（阿里云 Maven 镜像加速）。

## 1. 开通云托管 + 新建环境
1. 打开**微信开发者工具** → 顶部工具栏「云开发」旁的「**云托管**」；或浏览器 [mp.weixin.qq.com](https://mp.weixin.qq.com) → 左侧「**云托管**」。
2. 首次需开通（微信支付实名，有免费额度）。
3. 新建一个**环境**（选就近地域，如上海/广州），记下**环境 ID**。

## 2. 新建 MySQL 数据库
1. 云托管控制台 → 「**数据库**」→ MySQL → **新建实例**（选最小规格即可，省钱）。
2. 设置 root 密码；创建后记下：**内网地址**、**端口**(默认 3306)、**用户名**、**密码**。
3. 进入「数据库管理 / DMS」执行一句，建库（表由应用启动时自动创建）：
   ```sql
   CREATE DATABASE IF NOT EXISTS studybuddy DEFAULT CHARSET utf8mb4;
   ```

## 3. 新建服务并部署
1. 云托管 → 「**服务管理**」→ **新建服务**，名称如 `studybuddy-backend`。
2. 部署方式选 **代码仓库**（绑定 GitHub `www-win/check`，分支 `main`）或「本地代码上传」。
3. 关键填写：
   - **服务目录 / 构建目录**：`backend`
   - **Dockerfile 路径**：`backend/Dockerfile`（若上面已指定目录为 backend，则填 `Dockerfile`）
   - **监听端口**：`8080`

## 4. 配置环境变量（服务设置 → 环境变量）
| 变量 | 值 |
|---|---|
| `DB_URL` | `jdbc:mysql://<MySQL内网地址>:3306/studybuddy?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true` |
| `DB_USER` | MySQL 用户名（如 root） |
| `DB_PASSWORD` | MySQL 密码 |
| `JWT_SECRET` | 一串≥32位随机字符串（自定义，保密） |
| `WX_APPID` | `wxf6d8c62841394092` |
| `WX_SECRET` | 你的小程序 AppSecret |
| `OSS_*`（可选） | 配了才支持拍照打卡，见 application.yml |

## 5. 部署
- 点**部署 / 发布**，云托管会用 Dockerfile 构建镜像并启动（首次构建约 3-8 分钟，Maven 走阿里云镜像）。
- 构建成功、实例状态「运行中」即可。应用启动时会自动建好所有表。

## 6. 开公网访问 + 配小程序合法域名
1. 服务 → 「**公网访问 / 开启访问**」→ 得到一个 **HTTPS 域名**（云托管默认域名**免备案**）。
2. 小程序后台 [mp.weixin.qq.com](https://mp.weixin.qq.com) → 「**开发管理 → 开发设置 → 服务器域名**」→ 在 **request 合法域名** 添加该 HTTPS 域名；若用拍照打卡，**uploadFile 合法域名**也加上。

## 7. 小程序指向线上后端
1. 改 `miniprogram/src/utils/config.js`：
   ```js
   export const BASE_URL = 'https://你的云托管域名'
   ```
2. 重新编译 `npm run build:mp-weixin`，在微信开发者工具里**上传**为「体验版」。
3. 手机微信扫「体验版」二维码 → 即可在真机使用。
4. （正式对外）小程序后台提交审核 → 发布正式版。

## 排错
- **构建失败/超时**：已配阿里云 Maven 镜像；若仍慢，重试或换更高构建规格。
- **启动报数据库错**：确认 `studybuddy` 库已建、`DB_URL` 内网地址正确、账号密码无误。
- **登录失败**：核对 `WX_APPID` / `WX_SECRET`，且与该小程序匹配。
- **小程序请求被拦**：确认线上域名已加入「request 合法域名」，且 `BASE_URL` 用的是 https。

## 后续迭代（部署后照样能改）
改代码 → 推 GitHub → 云托管「重新部署」出新版本（可回滚）；小程序改完重新上传新版本。数据库数据不受影响。
