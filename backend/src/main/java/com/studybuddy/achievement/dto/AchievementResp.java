package com.studybuddy.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class AchievementResp {
    private String code;
    private String title;
    private String icon;
    private String desc;
    private int rewardPoints;
    private boolean unlocked;
    private LocalDateTime unlockedAt;
}
