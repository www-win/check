package com.studybuddy.achievement;

import com.studybuddy.achievement.dto.AchievementListResp;
import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/achievements")
@RequiredArgsConstructor
public class AchievementController {
    private final AchievementService achievementService;

    @GetMapping
    public R<AchievementListResp> list() {
        return R.ok(achievementService.list(CurrentUser.get()));
    }
}
