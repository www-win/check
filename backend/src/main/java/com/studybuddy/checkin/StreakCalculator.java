package com.studybuddy.checkin;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/** 连续天数计算（纯逻辑，无依赖，便于单测）。 */
@Component
public class StreakCalculator {

    /**
     * 从 anchor 往回逐日检查 dates 是否连续命中，返回连续天数。
     *
     * @param dates  用户签到日期集合（应已包含回看窗口内全部日期）
     * @param anchor 锚点日期（通常为最近一次签到日期 last_checkin_date）
     * @return 连续天数；anchor 为空或不在 dates 中时返回 0
     */
    public int currentStreak(Set<LocalDate> dates, LocalDate anchor) {
        if (anchor == null || dates == null || !dates.contains(anchor)) {
            return 0;
        }
        int streak = 0;
        LocalDate d = anchor;
        while (dates.contains(d)) {
            streak++;
            d = d.minusDays(1);
        }
        return streak;
    }
}
