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
