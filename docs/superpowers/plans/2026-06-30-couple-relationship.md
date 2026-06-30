# 情侣关系功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让两个用户用邀请码 + 对方同意建立情侣关系，建立后可互相查看打卡（含心情/笔记/图片）、查看共同统计、并发起"戳一下/留言督促"。

**Architecture:** 后端新增 `com.studybuddy.couple` 包（Controller/Service/entity/mapper/dto），复用现有 `CheckinService.status(userId)`/`calendar(userId, month)` 查看对方打卡，遵循 `R<T>` 包装、`CurrentUser.get()` 取登录用户、`BizException` 抛业务错。前端只做微信小程序：新增 `pages/couple/couple.vue` 一页按关系状态渲染，"我的"页加入口。

**Tech Stack:** Spring Boot + MyBatis-Plus（BaseMapper + LambdaQueryWrapper）、JUnit5 + Mockito（mock mapper，不连真实库）、MySQL 8、uni-app(Vue3) 微信小程序。

## Global Constraints

- 后端构建/测试从仓库根运行，使用便携工具链：测试 `mvn -f backend/pom.xml -B -ntp test`；打包 `mvn -f backend/pom.xml -B -ntp clean package`。若 PATH 未带工具链，先注入：`$env:JAVA_HOME="$env:USERPROFILE\tools\jdk-17.0.19+10"; $env:Path="$env:JAVA_HOME\bin;$env:USERPROFILE\tools\apache-maven-3.9.9\bin;$env:Path"`。
- 后端单元测试一律用 Mockito mock mapper，照搬 `CheckinServiceTest` 的 `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks` 风格，不依赖数据库。
- 所有接口走 `/api/couple/**`，已被 `WebMvcConfig` 的鉴权拦截器自动保护（非 `/api/auth/**`），无需额外配置；Controller 一律 `CurrentUser.get()` 取当前用户。
- 业务错误码统一用 404xx 区间（与现有 40010/40012/40100 不冲突）。错误码与文案见各任务。
- 关系唯一性、越权校验由 Service 逻辑保证：一个用户同一时间最多一个 active 关系；查看对方数据前必须校验调用方确有 active 关系。
- 小程序构建验证：`cd miniprogram && npm run build:mp-weixin`（产物进 `miniprogram/dist`，构建无报错即通过）。
- 提交信息用中文，前缀 `feat:`/`test:`/`docs:`，与现有 git history 一致。

---

## File Structure

**后端（新增）**
- `backend/src/main/java/com/studybuddy/couple/entity/Couple.java` — 关系实体
- `backend/src/main/java/com/studybuddy/couple/entity/CouplePoke.java` — 互动实体
- `backend/src/main/java/com/studybuddy/couple/mapper/CoupleMapper.java` — 关系 mapper
- `backend/src/main/java/com/studybuddy/couple/mapper/CouplePokeMapper.java` — 互动 mapper
- `backend/src/main/java/com/studybuddy/couple/dto/*.java` — 请求/响应 DTO
- `backend/src/main/java/com/studybuddy/couple/CoupleService.java` — 关系生命周期 + 对方数据 + 互动
- `backend/src/main/java/com/studybuddy/couple/CoupleController.java` — REST 入口

**后端（修改）**
- `backend/src/main/resources/db/schema.sql` — 追加 `couple`、`couple_poke` 表 + `user.invite_code` 唯一索引
- `backend/src/main/java/com/studybuddy/user/InviteCodeGenerator.java` — 邀请码生成（纯函数，便于测试）【新增】
- `backend/src/main/java/com/studybuddy/user/UserService.java` — 新增 `ensureInviteCode(userId)`
- `backend/src/main/java/com/studybuddy/checkin/mapper/CheckinRecordMapper.java` — 新增 `countCommonDays`

**后端测试（新增）**
- `backend/src/test/java/com/studybuddy/user/InviteCodeGeneratorTest.java`
- `backend/src/test/java/com/studybuddy/couple/CoupleServiceTest.java`

**前端（新增）**
- `miniprogram/src/pages/couple/couple.vue` — 情侣空间页

**前端（修改）**
- `miniprogram/src/utils/request.js` — 业务封装函数
- `miniprogram/src/pages.json` — 注册 couple 页
- `miniprogram/src/pages/profile/profile.vue` — 加"情侣空间"入口

---

## Task 1: 数据表、实体、邀请码生成

**Files:**
- Modify: `backend/src/main/resources/db/schema.sql`
- Create: `backend/src/main/java/com/studybuddy/couple/entity/Couple.java`
- Create: `backend/src/main/java/com/studybuddy/couple/entity/CouplePoke.java`
- Create: `backend/src/main/java/com/studybuddy/couple/mapper/CoupleMapper.java`
- Create: `backend/src/main/java/com/studybuddy/couple/mapper/CouplePokeMapper.java`
- Create: `backend/src/main/java/com/studybuddy/user/InviteCodeGenerator.java`
- Modify: `backend/src/main/java/com/studybuddy/user/UserService.java`
- Test: `backend/src/test/java/com/studybuddy/user/InviteCodeGeneratorTest.java`

**Interfaces:**
- Produces:
  - `Couple` 实体：`Long id; Long requesterId; Long targetId; Integer status; LocalDateTime createdAt; LocalDateTime confirmedAt;`（`status` 0=待确认 1=已建立）
  - `CouplePoke` 实体：`Long id; Long coupleId; Long fromUser; Long toUser; String message; LocalDateTime createdAt; LocalDateTime readAt;`
  - `CoupleMapper extends BaseMapper<Couple>`、`CouplePokeMapper extends BaseMapper<CouplePoke>`
  - `InviteCodeGenerator.generate(): String` —— 返回 6 位大写字母+数字（去易混字符）
  - `UserService.ensureInviteCode(Long userId): String` —— 用户无码则生成唯一码并落库，返回码

- [ ] **Step 1: 追加数据表与索引到 schema.sql**

在 `backend/src/main/resources/db/schema.sql` 末尾追加：

```sql

-- 情侣关系
CREATE TABLE IF NOT EXISTS `couple` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `requester_id` BIGINT   NOT NULL COMMENT '发起绑定方（输入了对方邀请码）',
  `target_id`    BIGINT   NOT NULL COMMENT '邀请码拥有方（待同意方）',
  `status`       TINYINT  NOT NULL COMMENT '0=待确认 1=已建立',
  `created_at`   DATETIME NOT NULL,
  `confirmed_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_requester` (`requester_id`),
  KEY `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 情侣互动（戳一下/留言）
CREATE TABLE IF NOT EXISTS `couple_poke` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `couple_id`  BIGINT   NOT NULL,
  `from_user`  BIGINT   NOT NULL,
  `to_user`    BIGINT   NOT NULL,
  `message`    VARCHAR(200) NULL COMMENT '空=纯戳一下，有值=留言督促',
  `created_at` DATETIME NOT NULL,
  `read_at`    DATETIME NULL COMMENT '对方已读时间',
  PRIMARY KEY (`id`),
  KEY `idx_couple` (`couple_id`),
  KEY `idx_to_unread` (`to_user`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 邀请码唯一（情侣绑定用）
ALTER TABLE `user` ADD UNIQUE KEY `uk_invite_code` (`invite_code`);
```

> 注：`ALTER TABLE ... ADD UNIQUE KEY` 在已存在该索引时会报错，仅在初始化新库时执行一次；schema.sql 是手动执行脚本，可接受。

- [ ] **Step 2: 创建 Couple 实体**

`backend/src/main/java/com/studybuddy/couple/entity/Couple.java`：

```java
package com.studybuddy.couple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("couple")
public class Couple {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long targetId;
    private Integer status; // 0=待确认 1=已建立
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}
```

- [ ] **Step 3: 创建 CouplePoke 实体**

`backend/src/main/java/com/studybuddy/couple/entity/CouplePoke.java`：

```java
package com.studybuddy.couple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("couple_poke")
public class CouplePoke {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long coupleId;
    private Long fromUser;
    private Long toUser;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
```

- [ ] **Step 4: 创建两个 Mapper**

`backend/src/main/java/com/studybuddy/couple/mapper/CoupleMapper.java`：

```java
package com.studybuddy.couple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.couple.entity.Couple;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoupleMapper extends BaseMapper<Couple> {
}
```

`backend/src/main/java/com/studybuddy/couple/mapper/CouplePokeMapper.java`：

```java
package com.studybuddy.couple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.couple.entity.CouplePoke;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouplePokeMapper extends BaseMapper<CouplePoke> {
}
```

- [ ] **Step 5: 写 InviteCodeGenerator 的失败测试**

`backend/src/test/java/com/studybuddy/user/InviteCodeGeneratorTest.java`：

```java
package com.studybuddy.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteCodeGeneratorTest {
    private final InviteCodeGenerator gen = new InviteCodeGenerator();

    @Test
    void generatesSixCharsFromAllowedAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = gen.generate();
            assertEquals(6, code.length(), "应为 6 位");
            assertTrue(code.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}"),
                    "只能含去易混字符的大写字母与数字: " + code);
        }
    }
}
```

- [ ] **Step 6: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -B -ntp test -Dtest=InviteCodeGeneratorTest`
Expected: 编译失败 / FAIL —— `InviteCodeGenerator` 不存在。

- [ ] **Step 7: 实现 InviteCodeGenerator**

`backend/src/main/java/com/studybuddy/user/InviteCodeGenerator.java`：

```java
package com.studybuddy.user;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 生成 6 位邀请码，去除易混字符（0/O/1/I）。 */
@Component
public class InviteCodeGenerator {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -B -ntp test -Dtest=InviteCodeGeneratorTest`
Expected: PASS。

- [ ] **Step 9: 给 UserService 增加 ensureInviteCode**

在 `backend/src/main/java/com/studybuddy/user/UserService.java` 中：
1. 顶部加构造注入 `InviteCodeGenerator`（`UserService` 已是 `@RequiredArgsConstructor`，加一个 `private final` 字段即可）。
2. 加方法。

修改后字段区与新增方法（`InviteCodeGenerator` 与 `UserService` 同包 `com.studybuddy.user`，无需 import）：

```java
// 其余 import 保持

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final InviteCodeGenerator inviteCodeGenerator;

    // ... findOrCreateByPhone / findOrCreateByOpenid 保持不变 ...

    /** 确保用户有邀请码：无则生成唯一码并落库，返回该码。 */
    public String ensureInviteCode(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new com.studybuddy.common.BizException(40100, "未登录");
        }
        if (u.getInviteCode() != null && !u.getInviteCode().isBlank()) {
            return u.getInviteCode();
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = inviteCodeGenerator.generate();
            boolean taken = userMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                            .eq(User::getInviteCode, code)) > 0;
            if (taken) {
                continue;
            }
            u.setInviteCode(code);
            u.setUpdatedAt(java.time.LocalDateTime.now());
            userMapper.updateById(u);
            return code;
        }
        throw new com.studybuddy.common.BizException(40410, "邀请码生成失败，请重试");
    }
}
```

- [ ] **Step 10: 编译确认整体通过**

Run: `mvn -f backend/pom.xml -B -ntp test -Dtest=InviteCodeGeneratorTest`
Expected: PASS（同时验证新增类编译无误）。

- [ ] **Step 11: 提交**

```bash
git add backend/src/main/resources/db/schema.sql backend/src/main/java/com/studybuddy/couple backend/src/main/java/com/studybuddy/user backend/src/test/java/com/studybuddy/user/InviteCodeGeneratorTest.java
git commit -m "feat: 情侣关系数据表、实体、邀请码生成"
```

---

## Task 2: 关系生命周期（绑定/同意/拒绝/取消/解除/状态）

**Files:**
- Create: `backend/src/main/java/com/studybuddy/couple/dto/CoupleStatusResp.java`
- Create: `backend/src/main/java/com/studybuddy/couple/dto/BindReq.java`
- Create: `backend/src/main/java/com/studybuddy/couple/CoupleService.java`
- Create: `backend/src/main/java/com/studybuddy/couple/CoupleController.java`
- Test: `backend/src/test/java/com/studybuddy/couple/CoupleServiceTest.java`

**Interfaces:**
- Consumes（Task 1）：`Couple`、`CoupleMapper`、`CouplePokeMapper`、`UserService.ensureInviteCode`。
- Produces：
  - `CoupleStatusResp { String status; String myInviteCode; Long coupleId; PartnerInfo partner; long unreadPokeCount; }`，内嵌 `PartnerInfo { String nickname; String avatar; }`；`status ∈ {NONE, PENDING_OUT, PENDING_IN, ACTIVE}`。
  - `CoupleService.status(Long me): CoupleStatusResp`
  - `CoupleService.bind(Long me, String inviteCode): void`
  - `CoupleService.accept(Long me): void`
  - `CoupleService.reject(Long me): void`
  - `CoupleService.cancel(Long me): void`
  - `CoupleService.unbind(Long me): void`
  - `CoupleService.findActive(Long userId): Couple`（包级可见，Task 3 复用）
  - `CoupleService.partnerId(Couple c, Long me): Long`（包级可见，Task 3 复用）

错误码：40400 不能和自己绑定 / 40401 你已有情侣关系 / 40402 对方已有情侣关系 / 40403 已发送过请求，等待对方同意 / 40404 邀请码无效 / 40405 没有待确认的请求 / 40406 未建立情侣关系。

- [ ] **Step 1: 创建 CoupleStatusResp DTO**

`backend/src/main/java/com/studybuddy/couple/dto/CoupleStatusResp.java`：

```java
package com.studybuddy.couple.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoupleStatusResp {
    private String status;        // NONE / PENDING_OUT / PENDING_IN / ACTIVE
    private String myInviteCode;  // 始终返回，方便分享
    private Long coupleId;        // ACTIVE 时有值
    private PartnerInfo partner;  // PENDING_*/ACTIVE 时有值
    private long unreadPokeCount; // ACTIVE 时对方戳我的未读数

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnerInfo {
        private String nickname;
        private String avatar;
    }
}
```

- [ ] **Step 2: 创建 BindReq DTO**

`backend/src/main/java/com/studybuddy/couple/dto/BindReq.java`：

```java
package com.studybuddy.couple.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindReq {
    @NotBlank(message = "请输入邀请码")
    private String inviteCode;
}
```

- [ ] **Step 3: 写 CoupleService 状态机失败测试**

`backend/src/test/java/com/studybuddy/couple/CoupleServiceTest.java`：

```java
package com.studybuddy.couple;

import com.studybuddy.checkin.CheckinService;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.couple.dto.CoupleStatusResp;
import com.studybuddy.couple.entity.Couple;
import com.studybuddy.couple.mapper.CoupleMapper;
import com.studybuddy.couple.mapper.CouplePokeMapper;
import com.studybuddy.user.UserService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoupleServiceTest {

    @Mock CoupleMapper coupleMapper;
    @Mock CouplePokeMapper pokeMapper;
    @Mock UserMapper userMapper;
    @Mock UserService userService;
    @Mock CheckinService checkinService;
    @Mock CheckinRecordMapper recordMapper;

    @InjectMocks CoupleService service;

    private final Long me = 1L;
    private final Long other = 2L;

    private User userWithCode(Long id, String code) {
        User u = new User();
        u.setId(id);
        u.setInviteCode(code);
        u.setNickname("用户" + id);
        return u;
    }

    private Couple active(Long a, Long b) {
        Couple c = new Couple();
        c.setId(99L);
        c.setRequesterId(a);
        c.setTargetId(b);
        c.setStatus(1);
        return c;
    }

    @Test
    void bindWithUnknownCodeThrows40404() {
        when(userMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.bind(me, "ZZZZZZ"));
        assertEquals(40404, e.getCode());
    }

    @Test
    void bindWithOwnCodeThrows40400() {
        when(userMapper.selectOne(any())).thenReturn(userWithCode(me, "ABC234"));
        BizException e = assertThrows(BizException.class, () -> service.bind(me, "ABC234"));
        assertEquals(40400, e.getCode());
    }

    @Test
    void bindWhenIAlreadyActiveThrows40401() {
        when(userMapper.selectOne(any())).thenReturn(userWithCode(other, "ABC234"));
        // findActive(me) 命中
        when(coupleMapper.selectOne(any())).thenReturn(active(me, other));
        BizException e = assertThrows(BizException.class, () -> service.bind(me, "ABC234"));
        assertEquals(40401, e.getCode());
    }

    @Test
    void bindSuccessInsertsPending() {
        when(userMapper.selectOne(any())).thenReturn(userWithCode(other, "ABC234"));
        // 两次 findActive(me)/findActive(other) 都无；selectCount(已有 pending) 为 0
        when(coupleMapper.selectOne(any())).thenReturn(null);
        when(coupleMapper.selectCount(any())).thenReturn(0L);

        service.bind(me, "ABC234");

        verify(coupleMapper, times(1)).insert(any(Couple.class));
    }

    @Test
    void acceptWithoutPendingThrows40405() {
        // findActive(me) 无；找待确认请求也无
        when(coupleMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.accept(me));
        assertEquals(40405, e.getCode());
    }

    @Test
    void statusNoneWhenNoRelation() {
        when(userService.ensureInviteCode(me)).thenReturn("ABC234");
        when(coupleMapper.selectOne(any())).thenReturn(null);
        CoupleStatusResp resp = service.status(me);
        assertEquals("NONE", resp.getStatus());
        assertEquals("ABC234", resp.getMyInviteCode());
    }

    @Test
    void statusActiveReturnsPartnerAndUnread() {
        when(userService.ensureInviteCode(me)).thenReturn("ABC234");
        // findActive(me) 命中
        when(coupleMapper.selectOne(any())).thenReturn(active(me, other));
        when(userMapper.selectById(other)).thenReturn(userWithCode(other, "QQQ234"));
        when(pokeMapper.selectCount(any())).thenReturn(3L);

        CoupleStatusResp resp = service.status(me);

        assertEquals("ACTIVE", resp.getStatus());
        assertEquals("用户2", resp.getPartner().getNickname());
        assertEquals(3L, resp.getUnreadPokeCount());
        assertEquals(99L, resp.getCoupleId());
    }

    @Test
    void unbindWithoutActiveThrows40406() {
        when(coupleMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.unbind(me));
        assertEquals(40406, e.getCode());
        verify(coupleMapper, never()).deleteById(any());
    }
}
```

> 说明：`CoupleService` 用多次不同条件调用 `coupleMapper.selectOne(...)`。上面用例每个场景里这些调用的返回值一致，故用单一 `when(...selectOne(any()))` 足够。实现时所有"查关系"都走 `selectOne`，保证测试可控；`status()` 里区分 active/pending 用各自的 `LambdaQueryWrapper`，但因为 mock 统一返回，测试只断言关键分支。`lenient()` 已 import 备用，若某用例出现 UnnecessaryStubbing 再改用。

- [ ] **Step 4: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -B -ntp test -Dtest=CoupleServiceTest`
Expected: 编译失败 / FAIL —— `CoupleService` 不存在。

- [ ] **Step 5: 实现 CoupleService（关系生命周期部分）**

`backend/src/main/java/com/studybuddy/couple/CoupleService.java`：

```java
package com.studybuddy.couple;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.checkin.CheckinService;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.common.BizException;
import com.studybuddy.couple.dto.CoupleStatusResp;
import com.studybuddy.couple.entity.Couple;
import com.studybuddy.couple.entity.CouplePoke;
import com.studybuddy.couple.mapper.CoupleMapper;
import com.studybuddy.couple.mapper.CouplePokeMapper;
import com.studybuddy.user.UserService;
import com.studybuddy.user.entity.User;
import com.studybuddy.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CoupleService {
    private final CoupleMapper coupleMapper;
    private final CouplePokeMapper pokeMapper;
    private final UserMapper userMapper;
    private final UserService userService;
    private final CheckinService checkinService;
    private final CheckinRecordMapper recordMapper;

    private static final int PENDING = 0;
    private static final int ACTIVE = 1;

    /** 关系总览。 */
    public CoupleStatusResp status(Long me) {
        String myCode = userService.ensureInviteCode(me);
        CoupleStatusResp resp = new CoupleStatusResp();
        resp.setStatus("NONE");
        resp.setMyInviteCode(myCode);

        Couple act = findActive(me);
        if (act != null) {
            Long pid = partnerId(act, me);
            resp.setStatus("ACTIVE");
            resp.setCoupleId(act.getId());
            resp.setPartner(partnerInfo(pid));
            long unread = pokeMapper.selectCount(new LambdaQueryWrapper<CouplePoke>()
                    .eq(CouplePoke::getToUser, me)
                    .isNull(CouplePoke::getReadAt));
            resp.setUnreadPokeCount(unread);
            return resp;
        }
        Couple out = pendingByRequester(me);
        if (out != null) {
            resp.setStatus("PENDING_OUT");
            resp.setPartner(partnerInfo(out.getTargetId()));
            return resp;
        }
        Couple in = pendingByTarget(me);
        if (in != null) {
            resp.setStatus("PENDING_IN");
            resp.setPartner(partnerInfo(in.getRequesterId()));
            return resp;
        }
        return resp;
    }

    /** 输入对方邀请码发起绑定。 */
    @Transactional
    public void bind(Long me, String inviteCode) {
        User target = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, inviteCode.trim().toUpperCase()));
        if (target == null) {
            throw new BizException(40404, "邀请码无效");
        }
        if (target.getId().equals(me)) {
            throw new BizException(40400, "不能和自己绑定");
        }
        if (findActive(me) != null) {
            throw new BizException(40401, "你已有情侣关系");
        }
        if (findActive(target.getId()) != null) {
            throw new BizException(40402, "对方已有情侣关系");
        }
        boolean dup = coupleMapper.selectCount(new LambdaQueryWrapper<Couple>()
                .eq(Couple::getRequesterId, me)
                .eq(Couple::getTargetId, target.getId())
                .eq(Couple::getStatus, PENDING)) > 0;
        if (dup) {
            throw new BizException(40403, "已发送过请求，等待对方同意");
        }
        Couple c = new Couple();
        c.setRequesterId(me);
        c.setTargetId(target.getId());
        c.setStatus(PENDING);
        c.setCreatedAt(LocalDateTime.now());
        coupleMapper.insert(c);
    }

    /** target 同意收到的请求。 */
    @Transactional
    public void accept(Long me) {
        Couple in = pendingByTarget(me);
        if (in == null) {
            throw new BizException(40405, "没有待确认的请求");
        }
        if (findActive(me) != null) {
            throw new BizException(40401, "你已有情侣关系");
        }
        if (findActive(in.getRequesterId()) != null) {
            throw new BizException(40402, "对方已有情侣关系");
        }
        in.setStatus(ACTIVE);
        in.setConfirmedAt(LocalDateTime.now());
        coupleMapper.updateById(in);
        // 清理我与发起方各自残留的其它 pending
        coupleMapper.delete(new LambdaQueryWrapper<Couple>()
                .eq(Couple::getStatus, PENDING)
                .and(w -> w.eq(Couple::getRequesterId, me).or().eq(Couple::getTargetId, me)
                        .or().eq(Couple::getRequesterId, in.getRequesterId())
                        .or().eq(Couple::getTargetId, in.getRequesterId())));
    }

    /** target 拒绝请求。 */
    @Transactional
    public void reject(Long me) {
        Couple in = pendingByTarget(me);
        if (in == null) {
            throw new BizException(40405, "没有待确认的请求");
        }
        coupleMapper.deleteById(in.getId());
    }

    /** requester 取消自己发出的请求。 */
    @Transactional
    public void cancel(Long me) {
        Couple out = pendingByRequester(me);
        if (out == null) {
            throw new BizException(40405, "没有待确认的请求");
        }
        coupleMapper.deleteById(out.getId());
    }

    /** 解除 active 关系。 */
    @Transactional
    public void unbind(Long me) {
        Couple act = findActive(me);
        if (act == null) {
            throw new BizException(40406, "未建立情侣关系");
        }
        coupleMapper.deleteById(act.getId());
    }

    // ---- 包级复用 ----

    Couple findActive(Long userId) {
        return coupleMapper.selectOne(new LambdaQueryWrapper<Couple>()
                .eq(Couple::getStatus, ACTIVE)
                .and(w -> w.eq(Couple::getRequesterId, userId).or().eq(Couple::getTargetId, userId))
                .last("limit 1"));
    }

    Long partnerId(Couple c, Long me) {
        return c.getRequesterId().equals(me) ? c.getTargetId() : c.getRequesterId();
    }

    /** 取当前用户的 active 关系，无则抛 40406。Task 3 的对方数据接口复用。 */
    Couple requireActive(Long me) {
        Couple act = findActive(me);
        if (act == null) {
            throw new BizException(40406, "未建立情侣关系");
        }
        return act;
    }

    // ---- 内部 ----

    private Couple pendingByRequester(Long userId) {
        return coupleMapper.selectOne(new LambdaQueryWrapper<Couple>()
                .eq(Couple::getStatus, PENDING)
                .eq(Couple::getRequesterId, userId)
                .orderByDesc(Couple::getId)
                .last("limit 1"));
    }

    private Couple pendingByTarget(Long userId) {
        return coupleMapper.selectOne(new LambdaQueryWrapper<Couple>()
                .eq(Couple::getStatus, PENDING)
                .eq(Couple::getTargetId, userId)
                .orderByDesc(Couple::getId)
                .last("limit 1"));
    }

    private CoupleStatusResp.PartnerInfo partnerInfo(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return new CoupleStatusResp.PartnerInfo("对方", null);
        }
        return new CoupleStatusResp.PartnerInfo(u.getNickname(), u.getAvatar());
    }
}
```

> 测试可控性说明：`status()` 的 active 分支先于 pending 分支，命中 `findActive` 即返回；测试 `statusActiveReturnsPartnerAndUnread` 中 `coupleMapper.selectOne(any())` 统一返回 active，符合该分支。`statusNoneWhenNoRelation` 统一返回 null，三处查询都为空，返回 NONE。

- [ ] **Step 6: 创建 CoupleController（生命周期接口）**

`backend/src/main/java/com/studybuddy/couple/CoupleController.java`：

```java
package com.studybuddy.couple;

import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import com.studybuddy.couple.dto.BindReq;
import com.studybuddy.couple.dto.CoupleStatusResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/couple")
@RequiredArgsConstructor
public class CoupleController {
    private final CoupleService coupleService;

    @GetMapping
    public R<CoupleStatusResp> status() {
        return R.ok(coupleService.status(CurrentUser.get()));
    }

    @PostMapping("/bind")
    public R<Void> bind(@Valid @RequestBody BindReq req) {
        coupleService.bind(CurrentUser.get(), req.getInviteCode());
        return R.ok();
    }

    @PostMapping("/accept")
    public R<Void> accept() {
        coupleService.accept(CurrentUser.get());
        return R.ok();
    }

    @PostMapping("/reject")
    public R<Void> reject() {
        coupleService.reject(CurrentUser.get());
        return R.ok();
    }

    @PostMapping("/cancel")
    public R<Void> cancel() {
        coupleService.cancel(CurrentUser.get());
        return R.ok();
    }

    @DeleteMapping
    public R<Void> unbind() {
        coupleService.unbind(CurrentUser.get());
        return R.ok();
    }
}
```

- [ ] **Step 7: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -B -ntp test -Dtest=CoupleServiceTest`
Expected: PASS（8 个用例全绿）。若出现 Mockito `UnnecessaryStubbingException`，把对应用例里多余的 `when(...)` 改成 `lenient().when(...)`。

- [ ] **Step 8: 提交**

```bash
git add backend/src/main/java/com/studybuddy/couple backend/src/test/java/com/studybuddy/couple/CoupleServiceTest.java
git commit -m "feat: 情侣关系生命周期（绑定/同意/拒绝/取消/解除/状态）"
```

---

## Task 3: 对方打卡查看、共同统计、戳一下/留言

**Files:**
- Modify: `backend/src/main/java/com/studybuddy/checkin/mapper/CheckinRecordMapper.java`
- Create: `backend/src/main/java/com/studybuddy/couple/dto/CoupleSummaryResp.java`
- Create: `backend/src/main/java/com/studybuddy/couple/dto/PokeReq.java`
- Create: `backend/src/main/java/com/studybuddy/couple/dto/PokeResp.java`
- Modify: `backend/src/main/java/com/studybuddy/couple/CoupleService.java`
- Modify: `backend/src/main/java/com/studybuddy/couple/CoupleController.java`
- Test: `backend/src/test/java/com/studybuddy/couple/CoupleServiceTest.java`（追加用例）

**Interfaces:**
- Consumes（Task 2）：`CoupleService.requireActive`、`partnerId`；`CheckinService.status(userId)`、`CheckinService.calendar(userId, month)`。
- Produces：
  - `CheckinRecordMapper.countCommonDays(Long a, Long b): int`
  - `CoupleSummaryResp { int commonDays; int myStreak; int partnerStreak; int totalPoints; }`
  - `PokeReq { String message; }`（可空，长度 ≤ 200）
  - `PokeResp { Long id; boolean fromMe; String message; LocalDateTime createdAt; }`
  - `CoupleService.partnerStatus(Long me): CheckinStatusResp`
  - `CoupleService.partnerCalendar(Long me, String month): CalendarResp`
  - `CoupleService.summary(Long me): CoupleSummaryResp`
  - `CoupleService.poke(Long me, String message): void`
  - `CoupleService.listPokes(Long me): List<PokeResp>`（同时把对方戳我的未读标记为已读）

- [ ] **Step 1: 给 CheckinRecordMapper 加共同打卡天数查询**

`backend/src/main/java/com/studybuddy/checkin/mapper/CheckinRecordMapper.java`：

```java
package com.studybuddy.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.checkin.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {

    /** 两人在同一天都打过卡的天数。 */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT checkin_date FROM checkin_record " +
            "  WHERE user_id IN (#{a}, #{b}) " +
            "  GROUP BY checkin_date HAVING COUNT(DISTINCT user_id) = 2" +
            ") t")
    int countCommonDays(@Param("a") Long a, @Param("b") Long b);
}
```

- [ ] **Step 2: 创建 CoupleSummaryResp / PokeReq / PokeResp DTO**

`backend/src/main/java/com/studybuddy/couple/dto/CoupleSummaryResp.java`：

```java
package com.studybuddy.couple.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CoupleSummaryResp {
    private int commonDays;
    private int myStreak;
    private int partnerStreak;
    private int totalPoints;
}
```

`backend/src/main/java/com/studybuddy/couple/dto/PokeReq.java`：

```java
package com.studybuddy.couple.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PokeReq {
    @Size(max = 200, message = "留言最多 200 字")
    private String message;
}
```

`backend/src/main/java/com/studybuddy/couple/dto/PokeResp.java`：

```java
package com.studybuddy.couple.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PokeResp {
    private Long id;
    private boolean fromMe;
    private String message;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 追加 CoupleServiceTest 用例**

在 `CoupleServiceTest` 类中追加（保持已有 import；新增的 import 一并加到文件顶部）：

```java
// 追加 import：
// import com.studybuddy.checkin.dto.CheckinStatusResp;
// import com.studybuddy.couple.dto.CoupleSummaryResp;
// import com.studybuddy.couple.entity.CouplePoke;

    @Test
    void partnerStatusWithoutActiveThrows40406() {
        when(coupleMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.partnerStatus(me));
        assertEquals(40406, e.getCode());
    }

    @Test
    void partnerStatusDelegatesToCheckinServiceWithPartnerId() {
        when(coupleMapper.selectOne(any())).thenReturn(active(me, other));
        CheckinStatusResp partnerResp = new CheckinStatusResp(true, 5, 9, 30, 120);
        when(checkinService.status(other)).thenReturn(partnerResp);

        CheckinStatusResp got = service.partnerStatus(me);

        assertEquals(5, got.getCurrentStreak());
        verify(checkinService, times(1)).status(other);
    }

    @Test
    void summaryCombinesBothSides() {
        when(coupleMapper.selectOne(any())).thenReturn(active(me, other));
        when(recordMapper.countCommonDays(any(), any())).thenReturn(7);
        when(checkinService.status(me)).thenReturn(new CheckinStatusResp(true, 3, 8, 20, 100));
        when(checkinService.status(other)).thenReturn(new CheckinStatusResp(false, 5, 9, 30, 150));

        CoupleSummaryResp s = service.summary(me);

        assertEquals(7, s.getCommonDays());
        assertEquals(3, s.getMyStreak());
        assertEquals(5, s.getPartnerStreak());
        assertEquals(250, s.getTotalPoints());
    }

    @Test
    void pokeWithoutActiveThrows40406() {
        when(coupleMapper.selectOne(any())).thenReturn(null);
        BizException e = assertThrows(BizException.class, () -> service.poke(me, "快打卡"));
        assertEquals(40406, e.getCode());
        verify(pokeMapper, never()).insert(any(CouplePoke.class));
    }

    @Test
    void pokeInsertsRowToPartner() {
        when(coupleMapper.selectOne(any())).thenReturn(active(me, other));
        service.poke(me, "快打卡");
        verify(pokeMapper, times(1)).insert(any(CouplePoke.class));
    }
```

- [ ] **Step 4: 运行测试确认失败**

Run: `mvn -f backend/pom.xml -B -ntp test -Dtest=CoupleServiceTest`
Expected: 编译失败 / FAIL —— `partnerStatus`/`summary`/`poke` 等方法不存在。

- [ ] **Step 5: 在 CoupleService 追加对方数据/统计/互动方法**

在 `CoupleService` 末尾（`requireActive` 已在 Task 2 定义）追加方法，并在文件顶部补充 import：

```java
// 顶部追加 import：
// import com.studybuddy.checkin.dto.CalendarResp;
// import com.studybuddy.checkin.dto.CheckinStatusResp;
// import com.studybuddy.couple.dto.CoupleSummaryResp;
// import com.studybuddy.couple.dto.PokeResp;
// import java.util.List;
// import java.util.stream.Collectors;

    /** 对方今日状态。 */
    public CheckinStatusResp partnerStatus(Long me) {
        Couple act = requireActive(me);
        return checkinService.status(partnerId(act, me));
    }

    /** 对方某月日历。 */
    public CalendarResp partnerCalendar(Long me, String month) {
        Couple act = requireActive(me);
        return checkinService.calendar(partnerId(act, me), month);
    }

    /** 共同统计。 */
    public CoupleSummaryResp summary(Long me) {
        Couple act = requireActive(me);
        Long pid = partnerId(act, me);
        int commonDays = recordMapper.countCommonDays(me, pid);
        CheckinStatusResp mine = checkinService.status(me);
        CheckinStatusResp theirs = checkinService.status(pid);
        return new CoupleSummaryResp(commonDays, mine.getCurrentStreak(),
                theirs.getCurrentStreak(), mine.getPoints() + theirs.getPoints());
    }

    /** 戳一下 / 留言督促。 */
    @Transactional
    public void poke(Long me, String message) {
        Couple act = requireActive(me);
        Long pid = partnerId(act, me);
        CouplePoke p = new CouplePoke();
        p.setCoupleId(act.getId());
        p.setFromUser(me);
        p.setToUser(pid);
        p.setMessage(message != null && message.isBlank() ? null : message);
        p.setCreatedAt(LocalDateTime.now());
        pokeMapper.insert(p);
    }

    /** 最近互动列表（最多 20 条），并把对方戳我的未读标为已读。 */
    @Transactional
    public List<PokeResp> listPokes(Long me) {
        Couple act = requireActive(me);
        List<CouplePoke> rows = pokeMapper.selectList(new LambdaQueryWrapper<CouplePoke>()
                .eq(CouplePoke::getCoupleId, act.getId())
                .orderByDesc(CouplePoke::getId)
                .last("limit 20"));
        // 标记对方戳我的为已读
        CouplePoke patch = new CouplePoke();
        patch.setReadAt(LocalDateTime.now());
        pokeMapper.update(patch, new LambdaQueryWrapper<CouplePoke>()
                .eq(CouplePoke::getToUser, me)
                .isNull(CouplePoke::getReadAt));
        return rows.stream()
                .map(r -> new PokeResp(r.getId(), r.getFromUser().equals(me),
                        r.getMessage(), r.getCreatedAt()))
                .collect(Collectors.toList());
    }
```

- [ ] **Step 6: 在 CoupleController 追加接口**

在 `CoupleController` 追加（补 import `CalendarResp`、`CheckinStatusResp`、`CoupleSummaryResp`、`PokeReq`、`PokeResp`、`List`、`GetMapping` 已存在、`RequestParam`）：

```java
// 顶部追加 import：
// import com.studybuddy.checkin.dto.CalendarResp;
// import com.studybuddy.checkin.dto.CheckinStatusResp;
// import com.studybuddy.couple.dto.CoupleSummaryResp;
// import com.studybuddy.couple.dto.PokeReq;
// import com.studybuddy.couple.dto.PokeResp;
// import org.springframework.web.bind.annotation.RequestParam;
// import java.util.List;

    @GetMapping("/partner/status")
    public R<CheckinStatusResp> partnerStatus() {
        return R.ok(coupleService.partnerStatus(CurrentUser.get()));
    }

    @GetMapping("/partner/calendar")
    public R<CalendarResp> partnerCalendar(@RequestParam String month) {
        return R.ok(coupleService.partnerCalendar(CurrentUser.get(), month));
    }

    @GetMapping("/summary")
    public R<CoupleSummaryResp> summary() {
        return R.ok(coupleService.summary(CurrentUser.get()));
    }

    @PostMapping("/poke")
    public R<Void> poke(@Valid @RequestBody PokeReq req) {
        coupleService.poke(CurrentUser.get(), req.getMessage());
        return R.ok();
    }

    @GetMapping("/pokes")
    public R<List<PokeResp>> pokes() {
        return R.ok(coupleService.listPokes(CurrentUser.get()));
    }
```

- [ ] **Step 7: 运行测试确认通过**

Run: `mvn -f backend/pom.xml -B -ntp test -Dtest=CoupleServiceTest`
Expected: PASS（含新追加用例）。

- [ ] **Step 8: 全量编译 + 全部测试**

Run: `mvn -f backend/pom.xml -B -ntp test`
Expected: BUILD SUCCESS，全部测试通过（含 Checkin/Streak 旧测试）。

- [ ] **Step 9: 提交**

```bash
git add backend/src/main/java/com/studybuddy/couple backend/src/main/java/com/studybuddy/checkin/mapper/CheckinRecordMapper.java backend/src/test/java/com/studybuddy/couple/CoupleServiceTest.java
git commit -m "feat: 情侣查看对方打卡、共同统计、戳一下/留言"
```

---

## Task 4: 小程序 API 封装、页面注册、入口

**Files:**
- Modify: `miniprogram/src/utils/request.js`
- Modify: `miniprogram/src/pages.json`
- Modify: `miniprogram/src/pages/profile/profile.vue`

**Interfaces:**
- Produces（供 Task 5/6 使用，均返回 `Promise`）：
  `getCouple()`、`bindCouple(inviteCode)`、`acceptCouple()`、`rejectCouple()`、`cancelCouple()`、`unbindCouple()`、`getPartnerStatus()`、`getPartnerCalendar(month)`、`getCoupleSummary()`、`pokePartner(message)`、`getPokes()`。

- [ ] **Step 1: 在 request.js 末尾追加情侣业务封装**

在 `miniprogram/src/utils/request.js` 文件末尾（现有 `clearGoal` 之后）追加：

```javascript

// ===== 情侣 =====
export const getCouple = () => request('/couple')
export const bindCouple = (inviteCode) => request('/couple/bind', { method: 'POST', data: { inviteCode } })
export const acceptCouple = () => request('/couple/accept', { method: 'POST' })
export const rejectCouple = () => request('/couple/reject', { method: 'POST' })
export const cancelCouple = () => request('/couple/cancel', { method: 'POST' })
export const unbindCouple = () => request('/couple', { method: 'DELETE' })
export const getPartnerStatus = () => request('/couple/partner/status')
export const getPartnerCalendar = (month) => request('/couple/partner/calendar?month=' + month)
export const getCoupleSummary = () => request('/couple/summary')
export const pokePartner = (message) => request('/couple/poke', { method: 'POST', data: { message: message || null } })
export const getPokes = () => request('/couple/pokes')
```

- [ ] **Step 2: 注册 couple 页到 pages.json**

在 `miniprogram/src/pages.json` 的 `pages` 数组中、`profile` 之后追加一项（非 tabBar 页）：

```json
    {
      "path": "pages/profile/profile",
      "style": {
        "navigationBarTitleText": "我的"
      }
    },
    {
      "path": "pages/couple/couple",
      "style": {
        "navigationBarTitleText": "情侣空间"
      }
    }
```

> 即把原 `profile` 项尾部逗号补上，并在其后新增 `couple` 项。`tabBar.list` 不变（couple 不是 tab）。

- [ ] **Step 3: 在"我的"页加入口**

修改 `miniprogram/src/pages/profile/profile.vue`，在统计卡 `<view class="card rows">…</view>` 与"退出登录"按钮之间插入入口行：

```html
    <view class="card entry" @tap="goCouple">
      <text>💑 情侣空间</text>
      <text class="arrow">›</text>
    </view>
```

并在 `<script setup>` 中加跳转函数（放在 `logout` 函数附近）：

```javascript
function goCouple() {
  uni.navigateTo({ url: '/pages/couple/couple' })
}
```

在 `<style scoped>` 末尾追加样式：

```css
.entry { display: flex; justify-content: space-between; align-items: center; margin-top: 24rpx; padding: 32rpx; font-size: 30rpx; font-weight: 600; }
.entry .arrow { color: var(--c-muted); font-size: 40rpx; }
```

- [ ] **Step 4: 构建验证**

Run: `cd miniprogram && npm run build:mp-weixin`
Expected: 构建成功、无报错（即便 couple 页尚未创建，pages.json 注册的路径在编译期不存在会报错——因此本步骤先创建一个最小占位页再构建，或将本步骤与 Task 5 Step 1 合并执行）。

> 执行提示：为避免 pages.json 指向不存在页面导致构建失败，**先做 Task 5 Step 1（创建 couple.vue 占位）再运行本步骤的构建**。Task 4 与 Task 5 可在同一次构建中验证。

- [ ] **Step 5: 提交**

```bash
git add miniprogram/src/utils/request.js miniprogram/src/pages.json miniprogram/src/pages/profile/profile.vue
git commit -m "feat: 小程序情侣 API 封装、页面注册与入口"
```

---

## Task 5: 情侣页 — 关系管理（绑定/同意/拒绝/取消/解除）

**Files:**
- Create: `miniprogram/src/pages/couple/couple.vue`

**Interfaces:**
- Consumes（Task 4）：`getCouple`、`bindCouple`、`acceptCouple`、`rejectCouple`、`cancelCouple`、`unbindCouple`、`toast`。
- Produces：`couple.vue` 渲染 `status ∈ {NONE, PENDING_OUT, PENDING_IN, ACTIVE}` 四态；ACTIVE 态的对方打卡/统计/互动内容在 Task 6 补全（本任务先放占位区块与"解除关系"）。

- [ ] **Step 1: 创建 couple.vue（关系管理骨架）**

`miniprogram/src/pages/couple/couple.vue`：

```vue
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  getCouple, bindCouple, acceptCouple, rejectCouple,
  cancelCouple, unbindCouple, toast
} from '../../utils/request'

const data = ref(null)        // CoupleStatusResp
const inputCode = ref('')
const submitting = ref(false)

onShow(() => load())

function load() {
  getCouple().then((d) => (data.value = d)).catch((e) => toast(e.message))
}

function copyCode() {
  if (!data.value || !data.value.myInviteCode) return
  uni.setClipboardData({ data: data.value.myInviteCode, success: () => toast('邀请码已复制') })
}

function doBind() {
  const code = inputCode.value.trim().toUpperCase()
  if (!code) return toast('请输入对方邀请码')
  submitting.value = true
  bindCouple(code)
    .then(() => { toast('已发送，等待对方同意'); inputCode.value = ''; load() })
    .catch((e) => toast(e.message))
    .finally(() => (submitting.value = false))
}

function doAccept() {
  acceptCouple().then(() => { toast('已结成情侣 💑'); load() }).catch((e) => toast(e.message))
}
function doReject() {
  rejectCouple().then(() => { toast('已拒绝'); load() }).catch((e) => toast(e.message))
}
function doCancel() {
  cancelCouple().then(() => { toast('已取消'); load() }).catch((e) => toast(e.message))
}
function doUnbind() {
  uni.showModal({
    title: '解除关系',
    content: '确定解除情侣关系？解除后将无法再查看对方打卡。',
    success: (r) => {
      if (!r.confirm) return
      unbindCouple().then(() => { toast('已解除'); load() }).catch((e) => toast(e.message))
    }
  })
}

defineExpose({ load }) // 供 Task 6 ACTIVE 内容刷新复用
</script>

<template>
  <view class="page-body">
    <block v-if="data">
      <!-- 未绑定 -->
      <block v-if="data.status === 'NONE'">
        <view class="card center">
          <view class="big">💑</view>
          <view class="title">和 TA 一起打卡</view>
          <view class="sub">把你的邀请码发给对方，或输入对方的邀请码</view>
          <view class="mycode" @tap="copyCode">
            <text class="code">{{ data.myInviteCode }}</text>
            <text class="copy">复制</text>
          </view>
        </view>
        <view class="card">
          <view class="field-label">输入对方邀请码</view>
          <input class="input" v-model="inputCode" placeholder="如 ABC234" maxlength="6" />
          <button class="btn" hover-class="btn-hover" :disabled="submitting" @tap="doBind">
            {{ submitting ? '发送中…' : '发起绑定' }}
          </button>
        </view>
      </block>

      <!-- 等待对方同意 -->
      <block v-else-if="data.status === 'PENDING_OUT'">
        <view class="card center">
          <view class="big">⏳</view>
          <view class="title">等待 {{ data.partner ? data.partner.nickname : 'TA' }} 同意</view>
          <view class="sub">对方同意后即可成为情侣</view>
          <button class="btn btn-ghost" @tap="doCancel">取消请求</button>
        </view>
      </block>

      <!-- 收到请求 -->
      <block v-else-if="data.status === 'PENDING_IN'">
        <view class="card center">
          <view class="big">💌</view>
          <view class="title">{{ data.partner ? data.partner.nickname : 'TA' }} 想和你组成情侣</view>
          <view class="sub">同意后你们可以互相查看打卡</view>
          <view class="row-actions">
            <button class="btn btn-ghost flex1" @tap="doReject">拒绝</button>
            <button class="btn flex1" hover-class="btn-hover" @tap="doAccept">同意</button>
          </view>
        </view>
      </block>

      <!-- 已建立：对方内容在 Task 6 补全 -->
      <block v-else-if="data.status === 'ACTIVE'">
        <view class="card center">
          <view class="big">💞</view>
          <view class="title">你和 {{ data.partner ? data.partner.nickname : 'TA' }} 已是情侣</view>
        </view>
        <!-- ACTIVE-CONTENT-SLOT (Task 6 在此插入对方打卡/统计/互动) -->
        <view class="link-danger" @tap="doUnbind">解除关系</view>
      </block>
    </block>
  </view>
</template>

<style scoped>
.card { background: #fff; border-radius: 32rpx; padding: 36rpx; margin-bottom: 24rpx; box-shadow: 0 4rpx 16rpx rgba(45,140,85,.06); }
.center { text-align: center; }
.big { font-size: 96rpx; }
.title { font-size: 36rpx; font-weight: 700; margin-top: 12rpx; }
.sub { font-size: 26rpx; color: var(--c-muted); margin-top: 10rpx; }
.mycode { display: inline-flex; align-items: center; gap: 18rpx; margin-top: 28rpx; background: #EAF6EF; border-radius: 24rpx; padding: 20rpx 32rpx; }
.code { font-size: 44rpx; font-weight: 800; letter-spacing: 6rpx; color: var(--c-primary-d); }
.copy { font-size: 24rpx; color: var(--c-primary-d); background: #fff; padding: 6rpx 20rpx; border-radius: 30rpx; }
.field-label { font-size: 26rpx; color: var(--c-muted); margin-bottom: 14rpx; }
.input { height: 92rpx; border: 2rpx solid var(--c-line); border-radius: 24rpx; padding: 0 28rpx; font-size: 36rpx; letter-spacing: 4rpx; text-align: center; margin-bottom: 24rpx; }
.row-actions { display: flex; gap: 20rpx; margin-top: 28rpx; }
.flex1 { flex: 1; }
.link-danger { text-align: center; color: #E06A5B; font-size: 26rpx; margin-top: 26rpx; }
</style>
```

- [ ] **Step 2: 构建验证**

Run: `cd miniprogram && npm run build:mp-weixin`
Expected: 构建成功、无报错（此时 pages.json 注册的 couple 页已存在）。

- [ ] **Step 3: 提交**

```bash
git add miniprogram/src/pages/couple/couple.vue
git commit -m "feat: 小程序情侣页关系管理（绑定/同意/拒绝/取消/解除）"
```

---

## Task 6: 情侣页 — 对方打卡、共同统计、戳一下/留言

**Files:**
- Modify: `miniprogram/src/pages/couple/couple.vue`

**Interfaces:**
- Consumes（Task 4）：`getPartnerStatus`、`getPartnerCalendar`、`getCoupleSummary`、`pokePartner`、`getPokes`、`toast`。
- Produces：ACTIVE 态完整内容——对方今日打卡卡片、共同统计卡片、对方本月日历（只读）、戳一下/留言、未读提示。

- [ ] **Step 1: 扩展 script —— 加载对方数据与互动**

在 `couple.vue` 的 `<script setup>` 中：
1. 补充 import：

```javascript
import { ref, computed } from 'vue'
import {
  getCouple, bindCouple, acceptCouple, rejectCouple,
  cancelCouple, unbindCouple,
  getPartnerStatus, getPartnerCalendar, getCoupleSummary, pokePartner, getPokes,
  toast
} from '../../utils/request'
```

2. 在 `const submitting = ref(false)` 之后新增状态与方法：

```javascript
const moodEmoji = { 1: '😣', 2: '😕', 3: '😐', 4: '🙂', 5: '😄' }
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
function pad(n) { return String(n).padStart(2, '0') }
const now = new Date()

const partner = ref(null)        // 对方 CheckinStatusResp
const summary = ref(null)        // CoupleSummaryResp
const cal = ref(null)            // 对方日历 CalendarResp
const calCur = ref({ y: now.getFullYear(), m: now.getMonth() + 1 })
const pokes = ref([])            // 互动列表
const showPoke = ref(false)
const pokeMsg = ref('')

const calCells = computed(() => {
  if (!cal.value) return []
  const first = new Date(calCur.value.y, calCur.value.m - 1, 1)
  const lead = (first.getDay() + 6) % 7
  const arr = []
  for (let i = 0; i < lead; i++) arr.push(null)
  cal.value.days.forEach((d) => arr.push(d))
  return arr
})
function dayNum(d) { return Number(d.date.slice(-2)) }
function calMonthStr() { return calCur.value.y + '-' + pad(calCur.value.m) }

function loadActive() {
  getPartnerStatus().then((d) => (partner.value = d)).catch((e) => toast(e.message))
  getCoupleSummary().then((d) => (summary.value = d)).catch((e) => toast(e.message))
  loadCalendar()
  getPokes().then((d) => (pokes.value = d)).catch(() => {})
}
function loadCalendar() {
  getPartnerCalendar(calMonthStr()).then((d) => (cal.value = d)).catch((e) => toast(e.message))
}
function calPrev() {
  let { y, m } = calCur.value
  m--; if (m < 1) { m = 12; y-- }
  calCur.value = { y, m }; loadCalendar()
}
function calNext() {
  let { y, m } = calCur.value
  m++; if (m > 12) { m = 1; y++ }
  calCur.value = { y, m }; loadCalendar()
}

function quickPoke() {
  pokePartner(null).then(() => { toast('已戳 TA 一下 👉'); getPokes().then((d) => (pokes.value = d)) })
    .catch((e) => toast(e.message))
}
function openPoke() { pokeMsg.value = ''; showPoke.value = true }
function sendPoke() {
  const msg = pokeMsg.value.trim()
  if (!msg) return toast('写点鼓励的话吧')
  pokePartner(msg).then(() => {
    toast('已送达 💌'); showPoke.value = false
    getPokes().then((d) => (pokes.value = d))
  }).catch((e) => toast(e.message))
}
```

3. 修改 `load()`：当状态为 ACTIVE 时拉取对方数据。把原 `load` 改为：

```javascript
function load() {
  getCouple().then((d) => {
    data.value = d
    if (d.status === 'ACTIVE') loadActive()
  }).catch((e) => toast(e.message))
}
```

> `defineExpose({ load })` 保留不变。

- [ ] **Step 2: 替换 ACTIVE 区块模板**

把 Task 5 模板里 ACTIVE 的整块 `<block v-else-if="data.status === 'ACTIVE'"> … </block>` 替换为：

```html
      <block v-else-if="data.status === 'ACTIVE'">
        <!-- 头部 -->
        <view class="card center hd">
          <view class="big">💞</view>
          <view class="title">你和 {{ data.partner ? data.partner.nickname : 'TA' }}</view>
          <view v-if="data.unreadPokeCount > 0" class="badge">TA 戳了你 {{ data.unreadPokeCount }} 次</view>
        </view>

        <!-- 对方今日打卡 -->
        <view class="card">
          <view class="card-title">TA 的今日打卡</view>
          <block v-if="partner">
            <view v-if="partner.todayChecked" class="today-ok">✅ 今天已打卡</view>
            <view v-else class="today-no">⌛ 今天还没打卡，戳戳 TA 吧</view>
            <view class="mini-stats">
              <view><text class="n">{{ partner.currentStreak }}</text><text class="l">连续</text></view>
              <view><text class="n">{{ partner.totalDays }}</text><text class="l">累计</text></view>
              <view><text class="n">{{ partner.points }}</text><text class="l">积分</text></view>
            </view>
          </block>
        </view>

        <!-- 共同统计 -->
        <view class="card" v-if="summary">
          <view class="card-title">我们的共同记录</view>
          <view class="mini-stats">
            <view><text class="n">{{ summary.commonDays }}</text><text class="l">共同打卡</text></view>
            <view><text class="n">{{ summary.myStreak }}/{{ summary.partnerStreak }}</text><text class="l">各自连续</text></view>
            <view><text class="n">{{ summary.totalPoints }}</text><text class="l">合计积分</text></view>
          </view>
        </view>

        <!-- 对方日历 -->
        <view class="card" v-if="cal">
          <view class="cal-head">
            <view class="nav" @tap="calPrev">‹</view>
            <view class="month">TA 的 {{ calCur.y }}年{{ calCur.m }}月</view>
            <view class="nav" @tap="calNext">›</view>
          </view>
          <view class="grid">
            <view v-for="w in weekdays" :key="w" class="wk">{{ w }}</view>
            <view
              v-for="(d, i) in calCells"
              :key="i"
              :class="['cell', d && d.status === 1 ? 'checked' : '', d && d.status === 2 ? 'makeup' : '']"
            >
              <block v-if="d">
                <text>{{ dayNum(d) }}</text>
                <text v-if="d.mood" class="cell-mood">{{ moodEmoji[d.mood] }}</text>
              </block>
            </view>
          </view>
        </view>

        <!-- 互动 -->
        <view class="row-actions">
          <button class="btn btn-ghost flex1" @tap="quickPoke">👉 戳一下</button>
          <button class="btn flex1" hover-class="btn-hover" @tap="openPoke">💌 留言督促</button>
        </view>

        <view class="card" v-if="pokes.length">
          <view class="card-title">最近互动</view>
          <view v-for="p in pokes" :key="p.id" class="poke-item">
            <text class="poke-who">{{ p.fromMe ? '我' : (data.partner ? data.partner.nickname : 'TA') }}</text>
            <text class="poke-msg">{{ p.message || '戳了一下 👉' }}</text>
          </view>
        </view>

        <view class="link-danger" @tap="doUnbind">解除关系</view>
      </block>
```

- [ ] **Step 3: 追加留言弹层 + 样式**

在 `<template>` 根 `view.page-body` 内、ACTIVE block 之后（仍在 `v-if="data"` 内或其外均可，放 `</block>` 收尾的外层之前）加入弹层：

```html
    <!-- 留言弹层 -->
    <view v-if="showPoke" class="mask" @tap="showPoke = false">
      <view class="sheet" @tap.stop>
        <view class="sheet-title">给 TA 留言</view>
        <textarea class="note" v-model="pokeMsg" maxlength="200" placeholder="今天也要加油打卡哦～" />
        <view class="sheet-actions">
          <button class="btn btn-ghost flex1" @tap="showPoke = false">取消</button>
          <button class="btn flex1" hover-class="btn-hover" @tap="sendPoke">发送</button>
        </view>
      </view>
    </view>
```

在 `<style scoped>` 末尾追加：

```css
.hd { position: relative; }
.badge { display: inline-block; margin-top: 14rpx; background: #FFE9E6; color: #E06A5B; font-size: 24rpx; padding: 8rpx 22rpx; border-radius: 30rpx; }
.card-title { font-size: 28rpx; font-weight: 700; margin-bottom: 20rpx; }
.today-ok { font-size: 30rpx; color: var(--c-primary-d); font-weight: 600; }
.today-no { font-size: 30rpx; color: var(--c-muted); }
.mini-stats { display: flex; margin-top: 20rpx; }
.mini-stats > view { flex: 1; text-align: center; display: flex; flex-direction: column; }
.mini-stats .n { font-size: 40rpx; font-weight: 800; color: var(--c-primary-d); }
.mini-stats .l { font-size: 24rpx; color: var(--c-muted); margin-top: 6rpx; }
.cal-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24rpx; }
.month { font-size: 30rpx; font-weight: 700; }
.nav { width: 64rpx; height: 64rpx; line-height: 60rpx; text-align: center; border-radius: 18rpx; background: #EAF6EF; color: var(--c-primary-d); font-size: 38rpx; }
.grid { display: flex; flex-wrap: wrap; }
.wk { width: 14.28%; text-align: center; font-size: 24rpx; color: var(--c-muted); padding-bottom: 12rpx; }
.cell { width: 14.28%; box-sizing: border-box; height: 84rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 28rpx; }
.cell text { line-height: 1.1; }
.cell .cell-mood { font-size: 20rpx; }
.cell.checked { background: var(--c-primary); color: #fff; border-radius: 18rpx; font-weight: 600; }
.cell.checked text { color: #fff; }
.cell.makeup { background: var(--c-accent); color: #fff; border-radius: 18rpx; font-weight: 600; }
.cell.makeup text { color: #fff; }
.poke-item { display: flex; gap: 16rpx; padding: 16rpx 0; border-bottom: 2rpx solid var(--c-line); font-size: 28rpx; }
.poke-item:last-child { border-bottom: none; }
.poke-who { color: var(--c-primary-d); font-weight: 700; flex-shrink: 0; }
.poke-msg { color: var(--c-text); }
.mask { position: fixed; left: 0; right: 0; top: 0; bottom: 0; background: rgba(20,32,26,.45); display: flex; align-items: flex-end; z-index: 100; }
.sheet { width: 100%; background: #fff; border-radius: 36rpx 36rpx 0 0; padding: 36rpx; }
.sheet-title { font-size: 36rpx; font-weight: 700; text-align: center; margin-bottom: 24rpx; }
.note { width: 100%; min-height: 140rpx; box-sizing: border-box; padding: 20rpx 24rpx; border: 2rpx solid var(--c-line); border-radius: 24rpx; font-size: 28rpx; }
.sheet-actions { display: flex; gap: 20rpx; margin-top: 28rpx; }
```

- [ ] **Step 4: 构建验证**

Run: `cd miniprogram && npm run build:mp-weixin`
Expected: 构建成功、无报错。

- [ ] **Step 5: 提交**

```bash
git add miniprogram/src/pages/couple/couple.vue
git commit -m "feat: 情侣页对方打卡/共同统计/戳一下留言"
```

---

## Task 7: 端到端联调与文档

**Files:**
- Modify: `docs/deploy-weixin-cloudrun.md`（如有"已实现接口"清单则补充；无则跳过）

- [ ] **Step 1: 后端全量构建**

Run: `mvn -f backend/pom.xml -B -ntp clean package`
Expected: BUILD SUCCESS。

- [ ] **Step 2: 手动联调脚本（开发库）**

前置：本地起后端、准备两个登录用户 A/B（各自 token）。用 curl/Apifox 按序验证：
1. A `GET /api/couple` → `status=NONE`，记下 `myInviteCode`(记为 CODE_A)。
2. B `POST /api/couple/bind {inviteCode: CODE_A}` → 成功；B `GET /api/couple` → `PENDING_OUT`。
3. A `GET /api/couple` → `PENDING_IN`；A `POST /api/couple/accept` → 成功。
4. A、B `GET /api/couple` → 均 `ACTIVE`，`partner` 为对方昵称。
5. A `GET /api/couple/partner/status`、`/summary`、`/partner/calendar?month=YYYY-MM` → 返回 B 的数据。
6. A `POST /api/couple/poke {message:"加油"}`；B `GET /api/couple` → `unreadPokeCount=1`；B `GET /api/couple/pokes` → 含该留言；B 再 `GET /api/couple` → `unreadPokeCount=0`。
7. 越权校验：解除前先让 B `DELETE /api/couple`；之后 A `GET /api/couple/partner/status` → 返回 40406。

记录每步实际结果，任一不符回到对应 Task 修复。

- [ ] **Step 3: 小程序真机/开发者工具走查**

`cd miniprogram && npm run build:mp-weixin`，用微信开发者工具打开 `miniprogram/dist/build/mp-weixin`（或 dev 模式），走查："我的"→"情侣空间"→ 复制邀请码 / 输入码绑定 / 同意 / 查看对方今日打卡与日历 / 戳一下 / 留言 / 解除关系，UI 与提示符合预期。

- [ ] **Step 4: 提交（如有文档改动）**

```bash
git add -A
git commit -m "docs: 情侣功能联调记录与接口补充"
```

---

## Self-Review 记录

- **Spec 覆盖**：邀请码+对方同意（Task 2）✓；互相看打卡含心情/笔记/图片（Task 3 复用 CheckinService.status/calendar，图片/心情/笔记在日历与今日卡呈现）✓；共同统计三项（Task 3 summary）✓；戳一下/留言+未读仅页面内（Task 3 poke/pokes + Task 6 badge）✓；唯一 active 关系与越权校验（Task 2/3 requireActive）✓；仅小程序前端（Task 4-6）✓；不做订阅消息/Web/历史关系（未列入任务）✓。
- **类型一致性**：`CoupleStatusResp`/`CoupleSummaryResp`/`PokeResp` 字段在 Service、Controller、前端封装、页面引用处保持一致；`findActive`/`partnerId`/`requireActive` 跨 Task 2→3 复用签名一致。
- **占位扫描**：无 TBD/TODO；每段含完整代码与命令。
