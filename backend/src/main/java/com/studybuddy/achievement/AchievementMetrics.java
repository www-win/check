package com.studybuddy.achievement;

/** 评估徽章所需的各项指标。字段 public,供 Badge 的判定 lambda 直接读取。 */
public class AchievementMetrics {
    public int maxStreak;
    public int totalDays;
    public boolean hasPhoto;
    public boolean hasNote;
    public int moodCount;
    public boolean coupleBound;
    public int commonDays;
}
