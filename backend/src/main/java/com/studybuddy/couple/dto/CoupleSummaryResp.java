package com.studybuddy.couple.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CoupleSummaryResp {
    private int commonDays;
    private int myStreak;
    private int partnerStreak;
    private int totalPoints;
}
