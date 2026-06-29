package com.studybuddy.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CheckinResp {
    private int currentStreak;
    private int maxStreak;
    private int totalDays;
    /** 本次获得积分 */
    private int pointsEarned;
    /** 当前积分余额 */
    private int points;
    /** 本次触发的里程碑天数（如 7/30/100），未触发为 null */
    private Integer milestone;
}
