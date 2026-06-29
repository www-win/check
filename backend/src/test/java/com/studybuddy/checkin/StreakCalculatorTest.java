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
}
