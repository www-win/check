package com.studybuddy.friend;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.common.BizException;
import com.studybuddy.friend.dto.FriendInfo;
import com.studybuddy.friend.dto.FriendListResp;
import com.studybuddy.friend.dto.FriendRequestInfo;
import com.studybuddy.friend.entity.Friendship;
import com.studybuddy.friend.mapper.FriendshipMapper;
import com.studybuddy.user.UserService;
import com.studybuddy.user.entity.User;
import com.studybuddy.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final FriendshipMapper friendshipMapper;
    private final UserMapper userMapper;
    private final UserService userService;

    private static final int PENDING = 0;
    private static final int ACCEPTED = 1;

    /** 好友列表 + 双向待确认请求 + 我的邀请码。 */
    public FriendListResp list(Long me) {
        String myCode = userService.ensureInviteCode(me);

        List<FriendInfo> friends = acceptedFriends(me);

        List<Friendship> incomingRows = friendshipMapper.selectList(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, PENDING)
                .eq(Friendship::getAddresseeId, me));
        List<FriendRequestInfo> incoming = new ArrayList<>();
        for (Friendship f : incomingRows) {
            incoming.add(toRequestInfo(f.getId(), f.getRequesterId()));
        }

        List<Friendship> outgoingRows = friendshipMapper.selectList(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, PENDING)
                .eq(Friendship::getRequesterId, me));
        List<FriendRequestInfo> outgoing = new ArrayList<>();
        for (Friendship f : outgoingRows) {
            outgoing.add(toRequestInfo(f.getId(), f.getAddresseeId()));
        }

        return new FriendListResp(myCode, friends, incoming, outgoing);
    }

    /** 用邀请码发好友请求。 */
    @Transactional
    public void request(Long me, String inviteCode) {
        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase();
        User target = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, code));
        if (target == null) {
            throw new BizException(41404, "邀请码无效");
        }
        if (target.getId().equals(me)) {
            throw new BizException(41400, "不能加自己为好友");
        }
        Long other = target.getId();
        boolean alreadyFriend = friendshipMapper.selectCount(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, ACCEPTED)
                .and(w -> w.nested(n -> n.eq(Friendship::getRequesterId, me).eq(Friendship::getAddresseeId, other))
                        .or().nested(n -> n.eq(Friendship::getRequesterId, other).eq(Friendship::getAddresseeId, me)))) > 0;
        if (alreadyFriend) {
            throw new BizException(41401, "你们已经是好友");
        }
        boolean pending = friendshipMapper.selectCount(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, PENDING)
                .and(w -> w.nested(n -> n.eq(Friendship::getRequesterId, me).eq(Friendship::getAddresseeId, other))
                        .or().nested(n -> n.eq(Friendship::getRequesterId, other).eq(Friendship::getAddresseeId, me)))) > 0;
        if (pending) {
            throw new BizException(41403, "已发送过请求,等待对方同意");
        }
        Friendship f = new Friendship();
        f.setRequesterId(me);
        f.setAddresseeId(other);
        f.setStatus(PENDING);
        f.setCreatedAt(LocalDateTime.now());
        try {
            friendshipMapper.insert(f);
        } catch (DuplicateKeyException e) {
            throw new BizException(41403, "已发送过请求,等待对方同意");
        }
    }

    /** 同意（仅被加方）。 */
    @Transactional
    public void accept(Long me, Long requestId) {
        Friendship f = requirePending(requestId);
        if (!f.getAddresseeId().equals(me)) {
            throw new BizException(41405, "请求不存在或无权操作");
        }
        f.setStatus(ACCEPTED);
        f.setAcceptedAt(LocalDateTime.now());
        friendshipMapper.updateById(f);
    }

    /** 拒绝（仅被加方）。 */
    @Transactional
    public void reject(Long me, Long requestId) {
        Friendship f = requirePending(requestId);
        if (!f.getAddresseeId().equals(me)) {
            throw new BizException(41405, "请求不存在或无权操作");
        }
        friendshipMapper.deleteById(requestId);
    }

    /** 撤回（仅发起方）。 */
    @Transactional
    public void cancel(Long me, Long requestId) {
        Friendship f = requirePending(requestId);
        if (!f.getRequesterId().equals(me)) {
            throw new BizException(41405, "请求不存在或无权操作");
        }
        friendshipMapper.deleteById(requestId);
    }

    /** 删除好友。 */
    @Transactional
    public void removeFriend(Long me, Long friendUserId) {
        int deleted = friendshipMapper.delete(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, ACCEPTED)
                .and(w -> w.nested(n -> n.eq(Friendship::getRequesterId, me).eq(Friendship::getAddresseeId, friendUserId))
                        .or().nested(n -> n.eq(Friendship::getRequesterId, friendUserId).eq(Friendship::getAddresseeId, me))));
        if (deleted == 0) {
            throw new BizException(41405, "请求不存在或无权操作");
        }
    }

    /** a、b 是否为 status=1 好友。 */
    public boolean areFriends(Long a, Long b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        return friendshipMapper.selectCount(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, ACCEPTED)
                .and(w -> w.nested(n -> n.eq(Friendship::getRequesterId, a).eq(Friendship::getAddresseeId, b))
                        .or().nested(n -> n.eq(Friendship::getRequesterId, b).eq(Friendship::getAddresseeId, a)))) > 0;
    }

    /** 我的所有 status=1 好友(含昵称头像)。 */
    public List<FriendInfo> acceptedFriends(Long me) {
        List<Friendship> accepted = friendshipMapper.selectList(new LambdaQueryWrapper<Friendship>()
                .eq(Friendship::getStatus, ACCEPTED)
                .and(w -> w.eq(Friendship::getRequesterId, me).or().eq(Friendship::getAddresseeId, me)));
        List<FriendInfo> friends = new ArrayList<>();
        for (Friendship f : accepted) {
            Long otherId = f.getRequesterId().equals(me) ? f.getAddresseeId() : f.getRequesterId();
            friends.add(toFriendInfo(otherId));
        }
        return friends;
    }

    // ---- 内部 ----

    private Friendship requirePending(Long requestId) {
        Friendship f = friendshipMapper.selectById(requestId);
        if (f == null || f.getStatus() == null || f.getStatus() != PENDING) {
            throw new BizException(41405, "请求不存在或无权操作");
        }
        return f;
    }

    private FriendInfo toFriendInfo(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return new FriendInfo(userId, "用户", null);
        }
        return new FriendInfo(userId, u.getNickname(), u.getAvatar());
    }

    private FriendRequestInfo toRequestInfo(Long requestId, Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            return new FriendRequestInfo(requestId, userId, "用户", null);
        }
        return new FriendRequestInfo(requestId, userId, u.getNickname(), u.getAvatar());
    }
}
