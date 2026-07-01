package com.studybuddy.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HeatmapSummary {
    private int totalDays;
    private int longestStreak;
    private int makeupDays;
    private int rate;
}
