package com.studybuddy.goal;

import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import com.studybuddy.goal.dto.GoalReq;
import com.studybuddy.goal.dto.GoalResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goal")
@RequiredArgsConstructor
public class GoalController {
    private final GoalService goalService;

    @GetMapping
    public R<GoalResp> get() {
        return R.ok(goalService.get(CurrentUser.get()));
    }

    @PutMapping
    public R<GoalResp> upsert(@Valid @RequestBody GoalReq req) {
        return R.ok(goalService.upsert(CurrentUser.get(), req));
    }

    @DeleteMapping
    public R<Void> clear() {
        goalService.clear(CurrentUser.get());
        return R.ok();
    }
}
