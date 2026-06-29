package com.studybuddy.checkin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** 打卡积分/补卡规则配置，对应 application.yml 的 studybuddy.checkin。 */
@Data
@Component
@ConfigurationProperties(prefix = "studybuddy.checkin")
public class CheckinProps {
    /** 每次正常签到基础积分 */
    private int basePoints = 10;
    /** 补卡消耗积分 */
    private int makeupCost = 50;
    /** 仅可补过去多少天内（不含今天） */
    private int makeupWindowDays = 7;
    /** 连续天数回看窗口上限 */
    private int streakWindowDays = 90;
    /** 连续里程碑 -> 额外奖励积分，例如 {7:20, 30:100, 100:300} */
    private Map<Integer, Integer> milestones = new HashMap<>();
}
