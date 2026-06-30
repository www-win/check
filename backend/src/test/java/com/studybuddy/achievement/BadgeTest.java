package com.studybuddy.achievement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeTest {

    private AchievementMetrics metrics() {
        return new AchievementMetrics();
    }

    @Test
    void catalogHasTenBadgesInFixedOrder() {
        Badge[] all = Badge.values();
        assertEquals(10, all.length);
        assertEquals(Badge.STREAK_3, all[0]);
        assertEquals(Badge.COUPLE_7, all[9]);
    }

    @Test
    void streakBadgesUseMaxStreak() {
        AchievementMetrics m = metrics();
        m.maxStreak = 7;
        assertTrue(Badge.STREAK_3.satisfied(m));
        assertTrue(Badge.STREAK_7.satisfied(m));
        assertFalse(Badge.STREAK_30.satisfied(m));
    }

    @Test
    void habitBadgesReadFlags() {
        AchievementMetrics m = metrics();
        m.totalDays = 1;
        m.hasPhoto = true;
        m.hasNote = false;
        m.moodCount = 10;
        assertTrue(Badge.FIRST_CHECKIN.satisfied(m));
        assertTrue(Badge.FIRST_PHOTO.satisfied(m));
        assertFalse(Badge.FIRST_NOTE.satisfied(m));
        assertTrue(Badge.MOOD_10.satisfied(m));
    }

    @Test
    void coupleBadges() {
        AchievementMetrics m = metrics();
        m.coupleBound = true;
        m.commonDays = 6;
        assertTrue(Badge.COUPLE_BOUND.satisfied(m));
        assertFalse(Badge.COUPLE_7.satisfied(m));
        m.commonDays = 7;
        assertTrue(Badge.COUPLE_7.satisfied(m));
    }

    @Test
    void rewardPointsAsSpecified() {
        assertEquals(500, Badge.STREAK_100.rewardPoints);
        assertEquals(10, Badge.FIRST_CHECKIN.rewardPoints);
    }
}
