# 修改昵称 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让用户在个人页修改本人昵称(后端 `PUT /api/user/nickname` + 两端个人页编辑入口)。

**Architecture:** 后端新增 `UserService.updateNickname` + `UserController`,复用现有 `user.nickname` 字段;前端两端在个人页加昵称编辑,保存后更新本地缓存与显示。

**Tech Stack:** Spring Boot + MyBatis-Plus + JUnit5/Mockito(后端);Vue 3(Web);uni-app/Vue 3(小程序)。

## Global Constraints

- 校验:trim 去首尾空格后,非空且 1-20 字符,否则抛 `BizException(40000, "昵称需为 1-20 个字符")`。
- 接口 `PUT /api/user/nickname`,body `{ nickname }`,返回 `{ nickname }`(trim 后的规范值)。
- 用户不存在抛 `BizException(40100, "未登录")`。
- 无数据库结构变更(复用 `user.nickname`)。
- 前端保存成功后,用后端返回的 `nickname` 更新本地缓存(Web `localStorage`、小程序 `storage`)与页面显示。
- 后端构建/测试从仓库根执行;若 PATH 未带工具链,先注入:
  `$env:JAVA_HOME="$env:USERPROFILE\tools\jdk-17.0.19+10"; $env:Path="$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.9\bin;$env:Path"`

---

### Task 1: 后端 UserService.updateNickname + DTO + 单测

**Files:**
- Create: `backend/src/main/java/com/studybuddy/user/dto/UpdateNicknameReq.java`
- Create: `backend/src/main/java/com/studybuddy/user/dto/UpdateNicknameResp.java`
- Modify: `backend/src/main/java/com/studybuddy/user/UserService.java`
- Test: `backend/src/test/java/com/studybuddy/user/UserServiceTest.java`

**Interfaces:**
- Consumes: `UserMapper`(现有 `BaseMapper<User>`,有 `selectById`/`updateById`);`InviteCodeGenerator`(UserService 现有构造依赖);`User` 实体(`setNickname`/`setUpdatedAt`);`BizException(int,String)`。
- Produces:
  - `UpdateNicknameReq { String nickname; }`(`@Data`)
  - `UpdateNicknameResp { String nickname; }`(`@Data @AllArgsConstructor`)
  - `UserService.updateNickname(Long userId, String nickname): UpdateNicknameResp`

- [ ] **Step 1: 写失败测试**

创建 `backend/src/test/java/com/studybuddy/user/UserServiceTest.java`:

```java
package com.studybuddy.user;

import com.studybuddy.common.BizException;
import com.studybuddy.user.dto.UpdateNicknameResp;
import com.studybuddy.user.entity.User;
import com.studybuddy.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserMapper userMapper;
    @Mock InviteCodeGenerator inviteCodeGenerator;

    @InjectMocks UserService service;

    private final Long uid = 1L;

    @Test
    void updateNicknameTrimsAndSaves() {
        User u = new User();
        u.setId(uid);
        when(userMapper.selectById(uid)).thenReturn(u);

        UpdateNicknameResp resp = service.updateNickname(uid, "  Alice  ");

        assertEquals("Alice", resp.getNickname());
        verify(userMapper).updateById(u);
        assertEquals("Alice", u.getNickname());
    }

    @Test
    void updateNicknameBlankThrows40000() {
        BizException e = assertThrows(BizException.class,
                () -> service.updateNickname(uid, "   "));
        assertEquals(40000, e.getCode());
        verify(userMapper, never()).updateById(any());
    }

    @Test
    void updateNicknameTooLongThrows40000() {
        String tooLong = "a".repeat(21);
        BizException e = assertThrows(BizException.class,
                () -> service.updateNickname(uid, tooLong));
        assertEquals(40000, e.getCode());
        verify(userMapper, never()).updateById(any());
    }

    @Test
    void updateNicknameUserNotFoundThrows40100() {
        when(userMapper.selectById(uid)).thenReturn(null);

        BizException e = assertThrows(BizException.class,
                () -> service.updateNickname(uid, "Bob"));
        assertEquals(40100, e.getCode());
        verify(userMapper, never()).updateById(any());
    }
}
```

- [ ] **Step 2: 运行测试,确认失败**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=UserServiceTest test`
Expected: 编译失败(`updateNickname`/`UpdateNicknameResp` 不存在)。

- [ ] **Step 3: 创建 UpdateNicknameReq**

创建 `backend/src/main/java/com/studybuddy/user/dto/UpdateNicknameReq.java`:

```java
package com.studybuddy.user.dto;

import lombok.Data;

@Data
public class UpdateNicknameReq {
    private String nickname;
}
```

- [ ] **Step 4: 创建 UpdateNicknameResp**

创建 `backend/src/main/java/com/studybuddy/user/dto/UpdateNicknameResp.java`:

```java
package com.studybuddy.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UpdateNicknameResp {
    private String nickname;
}
```

- [ ] **Step 5: 实现 updateNickname**

在 `backend/src/main/java/com/studybuddy/user/UserService.java` 顶部 import 区追加:

```java
import com.studybuddy.common.BizException;
import com.studybuddy.user.dto.UpdateNicknameResp;
```

在 `ensureInviteCode(...)` 方法之后(类的最后一个 `}` 之前)插入:

```java
    /** 修改本人昵称:trim 后须 1-20 字符。 */
    public UpdateNicknameResp updateNickname(Long userId, String nickname) {
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > 20) {
            throw new BizException(40000, "昵称需为 1-20 个字符");
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(40100, "未登录");
        }
        u.setNickname(name);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        return new UpdateNicknameResp(name);
    }
```

(`LocalDateTime` 在 UserService 已 import;`User`/`userMapper` 已在类中。)

- [ ] **Step 6: 运行测试,确认通过**

Run: `mvn -f backend/pom.xml -B -ntp -Dtest=UserServiceTest test`
Expected: BUILD SUCCESS,4 个用例通过。

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/studybuddy/user/dto backend/src/main/java/com/studybuddy/user/UserService.java backend/src/test/java/com/studybuddy/user/UserServiceTest.java
git commit -m "feat: 修改昵称 UserService.updateNickname + 单测"
```

---

### Task 2: 后端 UserController(PUT /api/user/nickname)

**Files:**
- Create: `backend/src/main/java/com/studybuddy/user/UserController.java`

**Interfaces:**
- Consumes: `UserService.updateNickname(Long,String)`(Task 1);`UpdateNicknameReq`/`UpdateNicknameResp`;`CurrentUser.get()`;`R.ok(...)`。
- Produces: HTTP `PUT /api/user/nickname` → `R<UpdateNicknameResp>`。

- [ ] **Step 1: 创建 UserController**

参照现有 `CheckinController`(`@RestController` + `R` 包裹 + `CurrentUser.get()`)。创建 `backend/src/main/java/com/studybuddy/user/UserController.java`:

```java
package com.studybuddy.user;

import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import com.studybuddy.user.dto.UpdateNicknameReq;
import com.studybuddy.user.dto.UpdateNicknameResp;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PutMapping("/nickname")
    public R<UpdateNicknameResp> updateNickname(@RequestBody UpdateNicknameReq req) {
        return R.ok(userService.updateNickname(CurrentUser.get(), req.getNickname()));
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -f backend/pom.xml -B -ntp compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 全量测试**

Run: `mvn -f backend/pom.xml -B -ntp test`
Expected: BUILD SUCCESS,所有测试通过(含 UserServiceTest)。

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/studybuddy/user/UserController.java
git commit -m "feat: 新增 PUT /api/user/nickname 修改昵称接口"
```

---

### Task 3: Web 端个人页改昵称

**Files:**
- Modify: `frontend/src/api.js`
- Modify: `frontend/src/views/Profile.vue`

**Interfaces:**
- Consumes: `PUT /api/user/nickname`(Task 2),返回 `{ nickname }`;现有 `request`、`toast`。
- Produces: `api.js` 导出 `updateNickname(nickname)`。

- [ ] **Step 1: api.js 加封装**

在 `frontend/src/api.js` 的"成就"区(`getAchievements` 之后)插入:

```javascript
// ===== 用户 =====
export function updateNickname(nickname) {
  return request('/user/nickname', { method: 'PUT', body: { nickname } })
}
```

- [ ] **Step 2: Profile.vue 改造昵称为可编辑**

把 `frontend/src/views/Profile.vue` 的 `<script setup>` 整体替换为:

```javascript
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { request, clearAuth, getAchievements, updateNickname } from '../api'
import { toast } from '../toast'

const router = useRouter()
const status = ref(null)
const ach = ref(null)
const nickname = ref(localStorage.getItem('nickname') || '同学')
const editing = ref(false)
const draft = ref('')
const saving = ref(false)

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
onMounted(load)

function startEdit() {
  draft.value = nickname.value
  editing.value = true
}

async function saveNickname() {
  saving.value = true
  try {
    const d = await updateNickname(draft.value)
    nickname.value = d.nickname
    localStorage.setItem('nickname', d.nickname)
    editing.value = false
    toast('昵称已更新')
  } catch (e) {
    toast(e.message)
  } finally {
    saving.value = false
  }
}

function logout() {
  clearAuth()
  router.push('/login')
}
```

- [ ] **Step 3: 替换 hero 模板**

把模板中的 `profile-hero` 块替换为:

```html
      <div class="profile-hero">
        <div class="avatar">{{ nickname.slice(0, 1) }}</div>
        <div v-if="!editing" class="profile-name">
          {{ nickname }}
          <button class="edit-name" type="button" @click="startEdit">✏️</button>
        </div>
        <div v-else class="name-edit">
          <input v-model="draft" class="name-input" maxlength="20" placeholder="输入昵称" />
          <button class="name-btn" type="button" :disabled="saving" @click="saveNickname">保存</button>
          <button class="name-btn ghost" type="button" :disabled="saving" @click="editing = false">取消</button>
        </div>
      </div>
```

- [ ] **Step 4: 加样式**

在 `frontend/src/views/Profile.vue` 的 `<style scoped>` 中(`.couple-entry .arrow {...}` 之后)追加:

```css
.edit-name {
  margin-left: 8px;
  background: transparent;
  border: none;
  font-size: 15px;
  cursor: pointer;
}
.name-edit { display: flex; align-items: center; gap: 8px; margin-top: 6px; }
.name-input {
  border: 1px solid var(--c-line);
  border-radius: 10px;
  padding: 6px 10px;
  font-size: 15px;
  width: 140px;
}
.name-btn {
  border: none;
  border-radius: 10px;
  padding: 6px 14px;
  font-size: 14px;
  font-weight: 600;
  background: var(--c-primary, #2E9E5B);
  color: #fff;
  cursor: pointer;
}
.name-btn.ghost { background: #eee; color: var(--c-text); }
.name-btn:disabled { opacity: 0.6; }
```

- [ ] **Step 5: 手动验证(若前端可运行)**

浏览器打开个人页 → 点昵称旁"✏️" → 输入新昵称 → 保存 → 顶部显示更新、刷新后仍保留(localStorage 已写)。空/超 20 字符后端会拦截并 toast。
若本会话不便启动前端,记为"待人工验证",不阻塞提交。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/api.js frontend/src/views/Profile.vue
git commit -m "feat(web): 个人页修改昵称"
```

---

### Task 4: 小程序端个人页改昵称

**Files:**
- Modify: `miniprogram/src/utils/request.js`
- Modify: `miniprogram/src/pages/profile/profile.vue`

**Interfaces:**
- Consumes: `PUT /api/user/nickname`(Task 2);现有 `request`、`toast`。
- Produces: `request.js` 导出 `updateNickname(nickname)`。

- [ ] **Step 1: request.js 加封装**

在 `miniprogram/src/utils/request.js` 的"业务封装"区(`getAchievements` 之后)插入:

```javascript
export const updateNickname = (nickname) => request('/user/nickname', { method: 'PUT', data: { nickname } })
```

- [ ] **Step 2: profile.vue 加 import 与编辑函数**

(a) 把 `miniprogram/src/pages/profile/profile.vue` 的 import 行改为:

```javascript
import { getStatus, getAchievements, updateNickname, toast } from '../../utils/request'
```

(b) 在 `goAchievements` 函数之后插入:

```javascript
function editName() {
  uni.showModal({
    title: '修改昵称',
    editable: true,
    placeholderText: '请输入昵称',
    content: nickname.value,
    success: (r) => {
      if (!r.confirm) return
      updateNickname(r.content)
        .then((d) => {
          nickname.value = d.nickname
          uni.setStorageSync('nickname', d.nickname)
          toast('昵称已更新')
        })
        .catch((e) => toast(e.message))
    }
  })
}
```

- [ ] **Step 3: 模板昵称加编辑入口**

把模板里的 `<view class="name">{{ nickname }}</view>` 这一行替换为:

```html
      <view class="name">{{ nickname }} <text class="edit-name" @tap="editName">编辑</text></view>
```

- [ ] **Step 4: 加样式**

在 `miniprogram/src/pages/profile/profile.vue` 的 `<style scoped>` 中(`.name {...}` 规则之后)追加:

```css
.edit-name { font-size: 24rpx; color: var(--c-primary-d); background: rgba(46,158,91,.1); padding: 4rpx 18rpx; border-radius: 28rpx; margin-left: 12rpx; font-weight: 600; }
```

- [ ] **Step 5: 手动验证(若可运行)**

小程序个人页 → 点昵称旁"编辑" → 弹框输入 → 确认 → 显示更新、storage 已写。空/超 20 字符后端拦截 toast。
若本会话不便构建,记为"待人工验证",不阻塞提交。

- [ ] **Step 6: 提交**

```bash
git add miniprogram/src/utils/request.js miniprogram/src/pages/profile/profile.vue
git commit -m "feat(mp): 个人页修改昵称"
```

---

## Self-Review

**Spec coverage:**
- 后端 PUT /api/user/nickname + updateNickname(trim/1-20 校验/40000/40100) → Task 1+2 ✓
- 复用 user.nickname,无 DB 变更 → Task 1 ✓
- Web 个人页编辑 + 更新 localStorage → Task 3 ✓
- 小程序个人页编辑 + 更新 storage → Task 4 ✓
- 测试(正常 trim/空/超长/用户不存在) → Task 1 UserServiceTest ✓

**Placeholder scan:** 无 TBD/TODO;手动验证步骤明确标注"可记待人工验证,不阻塞"。

**Type consistency:**
- `UserService.updateNickname(Long,String): UpdateNicknameResp` — Task 1 定义、Task 2 消费一致。
- `UpdateNicknameResp` 字段 `nickname`(getNickname)— Task 1 测试、Task 2 返回、前端 `d.nickname`(Task 3/4)一致。
- `UpdateNicknameReq.getNickname()` — Task 2 引用一致。
- 接口路径 `/api/user/nickname`(后端)对应前端 `request('/user/nickname', {method:'PUT'})`(BASE 含 /api)一致。
- 请求体键名 `nickname`:Web 用 `body:{nickname}`、小程序用 `data:{nickname}`,后端 `UpdateNicknameReq.nickname` 一致。
