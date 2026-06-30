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
