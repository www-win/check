package com.studybuddy.achievement;

import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import com.studybuddy.couple.entity.Couple;
import com.studybuddy.couple.mapper.CoupleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsCalculatorTest {

    @Mock CheckinStatMapper statMapper;
    @Mock CheckinRecordMapper recordMapper;
    @Mock CoupleMapper coupleMapper;

    @InjectMocks MetricsCalculator calculator;

    private final Long uid = 1L;

    @Test
    void computesFromStatRecordsAndCouple() {
        CheckinStat stat = new CheckinStat();
        stat.setMaxStreak(8);
        stat.setTotalDays(5);
        when(statMapper.selectById(uid)).thenReturn(stat);
        // selectCount 调用顺序: image, note, mood
        when(recordMapper.selectCount(any())).thenReturn(2L, 0L, 10L);

        Couple c = new Couple();
        c.setRequesterId(uid);
        c.setTargetId(2L);
        c.setStatus(1);
        when(coupleMapper.selectOne(any())).thenReturn(c);
        when(recordMapper.countCommonDays(eq(uid), eq(2L))).thenReturn(7);

        AchievementMetrics m = calculator.compute(uid);

        assertEquals(8, m.maxStreak);
        assertEquals(5, m.totalDays);
        assertTrue(m.hasPhoto);   // 2 > 0
        assertFalse(m.hasNote);   // 0 > 0 == false
        assertEquals(10, m.moodCount);
        assertTrue(m.coupleBound);
        assertEquals(7, m.commonDays);
    }

    @Test
    void noStatAndNoCoupleGivesZeros() {
        when(statMapper.selectById(uid)).thenReturn(null);
        when(recordMapper.selectCount(any())).thenReturn(0L, 0L, 0L);
        when(coupleMapper.selectOne(any())).thenReturn(null);

        AchievementMetrics m = calculator.compute(uid);

        assertEquals(0, m.maxStreak);
        assertEquals(0, m.totalDays);
        assertFalse(m.hasPhoto);
        assertFalse(m.coupleBound);
        assertEquals(0, m.commonDays);
    }
}
