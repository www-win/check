package com.studybuddy.achievement;

import com.studybuddy.achievement.dto.AchievementListResp;
import com.studybuddy.achievement.entity.UserAchievement;
import com.studybuddy.achievement.mapper.UserAchievementMapper;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock MetricsCalculator metricsCalculator;
    @Mock UserAchievementMapper achievementMapper;
    @Mock CheckinStatMapper statMapper;

    @InjectMocks AchievementService service;

    private final Long uid = 1L;

    private AchievementMetrics metricsStreak7Photo() {
        AchievementMetrics m = new AchievementMetrics();
        m.maxStreak = 7;       // 解锁 STREAK_3(20) + STREAK_7(50)
        m.totalDays = 1;       // FIRST_CHECKIN(10)
        m.hasPhoto = true;     // FIRST_PHOTO(20)
        return m;              // 共 4 个,合计 100 分
    }

    @Test
    void evaluateUnlocksSatisfiedBadgesAndAwardsPoints() {
        when(metricsCalculator.compute(uid)).thenReturn(metricsStreak7Photo());
        when(achievementMapper.selectList(any())).thenReturn(Collections.emptyList());
        CheckinStat stat = new CheckinStat();
        stat.setUserId(uid);
        stat.setPoints(30);
        when(statMapper.selectById(uid)).thenReturn(stat);

        List<Badge> newly = service.evaluate(uid);

        assertEquals(4, newly.size());
        assertTrue(newly.contains(Badge.STREAK_7));
        verify(achievementMapper, times(4)).insert(ArgumentMatchers.<UserAchievement>any());
        ArgumentCaptor<CheckinStat> cap = ArgumentCaptor.forClass(CheckinStat.class);
        verify(statMapper).updateById(cap.capture());
        assertEquals(130, cap.getValue().getPoints()); // 30 + 100
    }

    @Test
    void evaluateIsIdempotentForAlreadyUnlocked() {
        when(metricsCalculator.compute(uid)).thenReturn(metricsStreak7Photo());
        UserAchievement s3 = new UserAchievement();
        s3.setBadgeCode(Badge.STREAK_3.name());
        UserAchievement s7 = new UserAchievement();
        s7.setBadgeCode(Badge.STREAK_7.name());
        UserAchievement fc = new UserAchievement();
        fc.setBadgeCode(Badge.FIRST_CHECKIN.name());
        UserAchievement fp = new UserAchievement();
        fp.setBadgeCode(Badge.FIRST_PHOTO.name());
        when(achievementMapper.selectList(any())).thenReturn(List.of(s3, s7, fc, fp));

        List<Badge> newly = service.evaluate(uid);

        assertEquals(0, newly.size());
        verify(achievementMapper, never()).insert(ArgumentMatchers.<UserAchievement>any());
        verify(statMapper, never()).updateById(ArgumentMatchers.<CheckinStat>any());
    }

    @Test
    void evaluateCreatesStatWhenMissing() {
        AchievementMetrics m = new AchievementMetrics();
        m.totalDays = 1; // 仅 FIRST_CHECKIN(10)
        when(metricsCalculator.compute(uid)).thenReturn(m);
        when(achievementMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(statMapper.selectById(uid)).thenReturn(null);

        List<Badge> newly = service.evaluate(uid);

        assertEquals(1, newly.size());
        verify(statMapper).insert(ArgumentMatchers.<CheckinStat>any());
        verify(statMapper).updateById(ArgumentMatchers.<CheckinStat>any());
    }

    @Test
    void evaluateSkipsBadgeOnDuplicateKey() {
        AchievementMetrics m = new AchievementMetrics();
        m.totalDays = 1; // 仅 FIRST_CHECKIN 满足
        when(metricsCalculator.compute(uid)).thenReturn(m);
        when(achievementMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(achievementMapper.insert(ArgumentMatchers.<UserAchievement>any()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("dup"));

        java.util.List<Badge> newly = service.evaluate(uid);

        assertEquals(0, newly.size());
        verify(statMapper, never()).updateById(ArgumentMatchers.<CheckinStat>any());
    }

    @Test
    void listReturnsFullCatalogWithStatus() {
        AchievementMetrics none = new AchievementMetrics(); // 不满足任何条件
        when(metricsCalculator.compute(uid)).thenReturn(none);
        when(achievementMapper.selectList(any())).thenReturn(Collections.emptyList());

        AchievementListResp resp = service.list(uid);

        assertEquals(10, resp.getTotalCount());
        assertEquals(0, resp.getUnlockedCount());
        assertEquals(10, resp.getBadges().size());
        assertEquals("STREAK_3", resp.getBadges().get(0).getCode());
        assertEquals(0, resp.getNewlyUnlocked().size());
    }
}
