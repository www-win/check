package com.studybuddy.couple;

import com.studybuddy.checkin.dto.CalendarResp;
import com.studybuddy.checkin.dto.CheckinStatusResp;
import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import com.studybuddy.couple.dto.BindReq;
import com.studybuddy.couple.dto.CoupleSummaryResp;
import com.studybuddy.couple.dto.CoupleStatusResp;
import com.studybuddy.couple.dto.PokeReq;
import com.studybuddy.couple.dto.PokeResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
}
