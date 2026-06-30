package com.studybuddy.achievement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AchievementListResp {
    private int unlockedCount;
    private int totalCount;
    private List<AchievementResp> badges;
    private List<String> newlyUnlocked;
}
