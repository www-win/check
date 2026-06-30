package com.studybuddy.achievement;

import java.util.function.Predicate;

/** 徽章目录(固定 10 个,顺序即展示顺序)。 */
public enum Badge {
    STREAK_3("坚持三天", "🌱", "连续打卡 3 天", 20, m -> m.maxStreak >= 3),
    STREAK_7("一周不断", "🔥", "连续打卡 7 天", 50, m -> m.maxStreak >= 7),
    STREAK_30("满月坚持", "🌙", "连续打卡 30 天", 150, m -> m.maxStreak >= 30),
    STREAK_100("百日传奇", "👑", "连续打卡 100 天", 500, m -> m.maxStreak >= 100),
    FIRST_CHECKIN("初次打卡", "✅", "完成第一次打卡", 10, m -> m.totalDays >= 1),
    FIRST_PHOTO("影像记录", "📷", "第一次拍照打卡", 20, m -> m.hasPhoto),
    FIRST_NOTE("文字心声", "📝", "第一次写打卡笔记", 20, m -> m.hasNote),
    MOOD_10("心情观察家", "😊", "累计记录心情 10 次", 30, m -> m.moodCount >= 10),
    COUPLE_BOUND("心有灵犀", "💑", "成功绑定情侣", 50, m -> m.coupleBound),
    COUPLE_7("双向奔赴", "💞", "两人共同打卡 7 天", 100, m -> m.commonDays >= 7);

    public final String title;
    public final String icon;
    public final String desc;
    public final int rewardPoints;
    private final Predicate<AchievementMetrics> condition;

    Badge(String title, String icon, String desc, int rewardPoints, Predicate<AchievementMetrics> condition) {
        this.title = title;
        this.icon = icon;
        this.desc = desc;
        this.rewardPoints = rewardPoints;
        this.condition = condition;
    }

    public boolean satisfied(AchievementMetrics m) {
        return condition.test(m);
    }
}
