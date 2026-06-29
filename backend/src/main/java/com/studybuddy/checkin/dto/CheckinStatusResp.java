package com.studybuddy.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckinStatusResp {
    private boolean todayChecked;
    private int currentStreak;
    private int maxStreak;
    private int totalDays;
    private int points;
}
