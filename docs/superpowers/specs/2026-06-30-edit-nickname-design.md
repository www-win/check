# 修改昵称 设计

日期:2026-06-30

## 背景与目标

用户当前的昵称由系统在登录/注册时自动生成(手机号尾号或"微信用户"),无法自行修改。本功能让用户在个人页修改自己的昵称。

## 范围

- 用户修改**本人**昵称。
- 校验:trim 去首尾空格后,非空且 1-20 字符。
- 无数据库结构变更(复用现有 `user.nickname`)。

## 后端设计

### 接口

`PUT /api/user/nickname`(需登录),请求体:

```json
{ "nickname": "新昵称" }
```

响应 `{ "nickname": "新昵称" }`(后端 trim 规范化后的值)。

### UserController(新建)

```java
@PutMapping("/nickname")
public R<UpdateNicknameResp> updateNickname(@RequestBody UpdateNicknameReq req) {
    return R.ok(userService.updateNickname(CurrentUser.get(), req.getNickname()));
}
```

`@RestController @RequestMapping("/api/user")`。

### DTO

- `UpdateNicknameReq { String nickname; }`
- `UpdateNicknameResp { String nickname; }`(`@Data @AllArgsConstructor`)

### UserService.updateNickname(Long userId, String nickname)

1. `name = nickname == null ? "" : nickname.trim()`。
2. 校验:`name.isEmpty() || name.length() > 20` → 抛 `BizException(40000, "昵称需为 1-20 个字符")`。
3. 查 user(`userMapper.selectById`),null → 抛 `BizException(40100, "未登录")`(与 ensureInviteCode 一致)。
4. `user.setNickname(name); user.setUpdatedAt(now); userMapper.updateById(user)`。
5. 返回 `new UpdateNicknameResp(name)`。

### 错误码

| 码 | 含义 |
|----|------|
| 40000 | 昵称需为 1-20 个字符 |

## 前端设计(两端,均在个人页)

### Web(frontend/src/views/Profile.vue)

- 个人页 hero 区昵称旁加"✏️ 编辑"。点击切换为输入框(预填当前昵称)+ "保存"/"取消"。
- 保存:调 `updateNickname(name)` → 成功后用返回的 `nickname` 更新 `localStorage('nickname')` 与页面显示的 `nickname`,退出编辑态;失败 `toast(e.message)`。
- `api.js` 加 `updateNickname(nickname)`:`request('/user/nickname', { method: 'PUT', body: { nickname } })`。

### 小程序(miniprogram/src/pages/profile/profile.vue)

- hero 区昵称旁加"编辑"。点击 `uni.showModal({ title: '修改昵称', editable: true, placeholderText: '请输入昵称', content: 当前昵称 })`。
- 确认(`r.confirm`):取 `r.content`,调 `updateNickname(content)` → 成功后用返回的 `nickname` 更新 `uni.setStorageSync('nickname', ...)` 与显示;失败 `toast(e.message)`。
- `utils/request.js` 加 `updateNickname = (nickname) => request('/user/nickname', { method: 'PUT', data: { nickname } })`。

## 测试

`UserServiceTest`(JUnit5 + Mockito,mock `UserMapper` 等):
1. 正常:`updateNickname` trim 首尾空格后更新,返回 trim 值,调用 `updateById`。
2. 空/纯空白 → 抛 `40000`,不调用 `updateById`。
3. 超 20 字符 → 抛 `40000`。
4. 用户不存在(selectById 返回 null)→ 抛 `40100`。

## 非目标(YAGNI)

- 不做修改头像(本期仅昵称)。
- 不加 GET 用户信息接口(昵称本地已缓存)。
- 不做昵称敏感词过滤(后续可加)。
- 不做昵称唯一性(允许重名)。
