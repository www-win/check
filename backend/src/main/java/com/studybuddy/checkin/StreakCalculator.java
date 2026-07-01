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

    /**
     * 全集合内最长连续日期段长度。仅从每个「段起点」（前一天不在集合中）向后数，O(n)。
     *
     * @param dates 打卡日期集合（signed + makeup 并集）
     * @return 最长连续段天数；空集合返回 0
     */
    public int longestStreak(Set<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return 0;
        }
        int longest = 0;
        for (LocalDate d : dates) {
            if (!dates.contains(d.minusDays(1))) {
                int len = 1;
                LocalDate next = d.plusDays(1);
                while (dates.contains(next)) {
                    len++;
                    next = next.plusDays(1);
                }
                longest = Math.max(longest, len);
            }
        }
        return longest;
    }

    /**
     * 打卡率（整数百分比）。分母：往年=全年天数；今年=年初到 today 的已过天数（含今天）；未来年=0。
     *
     * @param totalDays 该年打卡总天数
     * @param year      目标年份
     * @param today     当前日期（注入以便测试）
     */
    public int rate(int totalDays, int year, LocalDate today) {
        if (totalDays <= 0) {
            return 0;
        }
        int denom;
        if (year < today.getYear()) {
            denom = java.time.Year.of(year).length();
        } else if (year == today.getYear()) {
            denom = today.getDayOfYear();
        } else {
            return 0;
        }
        if (denom <= 0) {
            return 0;
        }
        return (int) Math.round(totalDays * 100.0 / denom);
    }
}
