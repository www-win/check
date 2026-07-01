package com.studybuddy.checkin;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreakCalculatorTest {
    private final StreakCalculator calc = new StreakCalculator();

    private Set<LocalDate> datesOf(LocalDate... ds) {
        return new HashSet<>(Set.of(ds));
    }

    @Test
    void nullAnchorReturnsZero() {
        assertEquals(0, calc.currentStreak(datesOf(LocalDate.of(2026, 6, 1)), null));
    }

    @Test
    void anchorNotInSetReturnsZero() {
        Set<LocalDate> dates = datesOf(LocalDate.of(2026, 6, 1));
        assertEquals(0, calc.currentStreak(dates, LocalDate.of(2026, 6, 2)));
    }

    @Test
    void singleDayReturnsOne() {
        LocalDate d = LocalDate.of(2026, 6, 10);
        assertEquals(1, calc.currentStreak(datesOf(d), d));
    }

    @Test
    void threeConsecutiveDaysReturnsThree() {
        Set<LocalDate> dates = datesOf(
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 6, 10));
        assertEquals(3, calc.currentStreak(dates, LocalDate.of(2026, 6, 10)));
    }

    @Test
    void gapBreaksStreakAndOnlyTrailingCounts() {
        // 周一签、周二漏、周三签：以周三为锚，连续仅 1
        Set<LocalDate> dates = datesOf(
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 10));
        assertEquals(1, calc.currentStreak(dates, LocalDate.of(2026, 6, 10)));
    }

    @Test
    void makeupFillsGapBecomesContinuous() {
        // 补卡周二后，周一二三齐全：以周三为锚，连续 3
        Set<LocalDate> dates = datesOf(
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                LocalDate.of(2026, 6, 10));
        assertEquals(3, calc.currentStreak(dates, LocalDate.of(2026, 6, 10)));
    }

    @Test
    void crossMonthBoundaryCountsCorrectly() {
        Set<LocalDate> dates = datesOf(
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2));
        assertEquals(3, calc.currentStreak(dates, LocalDate.of(2026, 6, 2)));
    }

    // ---- longestStreak ----

    @Test
    void longestStreakEmptyReturnsZero() {
        assertEquals(0, calc.longestStreak(new HashSet<>()));
    }

    @Test
    void longestStreakSingleReturnsOne() {
        assertEquals(1, calc.longestStreak(datesOf(LocalDate.of(2026, 3, 1))));
    }

    @Test
    void longestStreakAllConsecutive() {
        Set<LocalDate> dates = datesOf(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 3),
                LocalDate.of(2026, 3, 4));
        assertEquals(4, calc.longestStreak(dates));
    }

    @Test
    void longestStreakPicksLongestOfMultipleSegments() {
        // 段1: 3/1~3/2 (2) ; 段2: 3/5~3/8 (4) ; 段3: 3/20 (1)
        Set<LocalDate> dates = datesOf(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 6),
                LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 20));
        assertEquals(4, calc.longestStreak(dates));
    }

    @Test
    void longestStreakCrossMonth() {
        Set<LocalDate> dates = datesOf(
                LocalDate.of(2026, 1, 30), LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 2));
        assertEquals(4, calc.longestStreak(dates));
    }

    // ---- rate ----

    @Test
    void ratePastYearFullIs100() {
        // 2025 满勤（365 天），锚定 today 在 2026
        assertEquals(100, calc.rate(365, 2025, LocalDate.of(2026, 7, 1)));
    }

    @Test
    void ratePastYearPartial() {
        // 2025 打卡 73 天 / 365 = 20%
        assertEquals(20, calc.rate(73, 2025, LocalDate.of(2026, 7, 1)));
    }

    @Test
    void rateCurrentYearUsesElapsedDays() {
        // today = 2026-01-10（第 10 天），打卡 5 天 => 50%
        assertEquals(50, calc.rate(5, 2026, LocalDate.of(2026, 1, 10)));
    }

    @Test
    void rateFutureYearIsZero() {
        assertEquals(0, calc.rate(0, 2027, LocalDate.of(2026, 7, 1)));
    }

    @Test
    void rateZeroTotalIsZero() {
        assertEquals(0, calc.rate(0, 2025, LocalDate.of(2026, 7, 1)));
    }
}
