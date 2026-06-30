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
