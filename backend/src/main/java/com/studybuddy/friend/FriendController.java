package com.studybuddy.friend;

import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import com.studybuddy.friend.dto.AddFriendReq;
import com.studybuddy.friend.dto.FriendListResp;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;

    @GetMapping
    public R<FriendListResp> list() {
        return R.ok(friendService.list(CurrentUser.get()));
    }

    @PostMapping("/requests")
    public R<Void> request(@RequestBody AddFriendReq req) {
        friendService.request(CurrentUser.get(), req.getInviteCode());
        return R.ok();
    }

    @PostMapping("/requests/{id}/accept")
    public R<Void> accept(@PathVariable Long id) {
        friendService.accept(CurrentUser.get(), id);
        return R.ok();
    }

    @PostMapping("/requests/{id}/reject")
    public R<Void> reject(@PathVariable Long id) {
        friendService.reject(CurrentUser.get(), id);
        return R.ok();
    }

    @PostMapping("/requests/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        friendService.cancel(CurrentUser.get(), id);
        return R.ok();
    }

    @DeleteMapping("/{friendUserId}")
    public R<Void> remove(@PathVariable Long friendUserId) {
        friendService.removeFriend(CurrentUser.get(), friendUserId);
        return R.ok();
    }
}
