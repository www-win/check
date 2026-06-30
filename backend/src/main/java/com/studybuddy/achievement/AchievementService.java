package com.studybuddy.achievement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.achievement.dto.AchievementListResp;
import com.studybuddy.achievement.dto.AchievementResp;
import com.studybuddy.achievement.entity.UserAchievement;
import com.studybuddy.achievement.mapper.UserAchievementMapper;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AchievementService {
    private final MetricsCalculator metricsCalculator;
    private final UserAchievementMapper achievementMapper;
    private final CheckinStatMapper statMapper;

    /** 评估并解锁新满足条件的徽章,发奖励积分。返回本次新解锁。 */
    @Transactional
    public List<Badge> evaluate(Long userId) {
        AchievementMetrics m = metricsCalculator.compute(userId);
        Set<String> unlocked = unlockedCodes(userId);

        List<Badge> newly = new ArrayList<>();
        for (Badge b : Badge.values()) {
            if (unlocked.contains(b.name())) continue;
            if (!b.satisfied(m)) continue;
            UserAchievement ua = new UserAchievement();
            ua.setUserId(userId);
            ua.setBadgeCode(b.name());
            ua.setUnlockedAt(LocalDateTime.now());
            ua.setPointsAwarded(b.rewardPoints);
            try {
                achievementMapper.insert(ua);
            } catch (DuplicateKeyException e) {
                continue;
            }
            newly.add(b);
        }
        if (!newly.isEmpty()) {
            int sum = newly.stream().mapToInt(b -> b.rewardPoints).sum();
            CheckinStat stat = ensureStat(userId);
            stat.setPoints(n(stat.getPoints()) + sum);
            stat.setUpdatedAt(LocalDateTime.now());
            statMapper.updateById(stat);
        }
        return newly;
    }

    /** 评估后返回全目录(含解锁状态)。 */
    @Transactional
    public AchievementListResp list(Long userId) {
        List<Badge> newly = evaluate(userId);
        Map<String, UserAchievement> mine = achievementMapper.selectList(
                        new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserAchievement::getBadgeCode, x -> x, (a, b) -> a));

        List<AchievementResp> badges = new ArrayList<>();
        for (Badge b : Badge.values()) {
            UserAchievement ua = mine.get(b.name());
            badges.add(new AchievementResp(b.name(), b.title, b.icon, b.desc, b.rewardPoints,
                    ua != null, ua == null ? null : ua.getUnlockedAt()));
        }
        List<String> newlyCodes = newly.stream().map(Badge::name).collect(Collectors.toList());
        return new AchievementListResp(mine.size(), Badge.values().length, badges, newlyCodes);
    }

    // ---- 内部 ----

    private Set<String> unlockedCodes(Long userId) {
        return achievementMapper.selectList(new LambdaQueryWrapper<UserAchievement>()
                        .eq(UserAchievement::getUserId, userId))
                .stream().map(UserAchievement::getBadgeCode).collect(Collectors.toSet());
    }

    private CheckinStat ensureStat(Long userId) {
        CheckinStat stat = statMapper.selectById(userId);
        if (stat == null) {
            stat = new CheckinStat();
            stat.setUserId(userId);
            stat.setCurrentStreak(0);
            stat.setMaxStreak(0);
            stat.setTotalDays(0);
            stat.setPoints(0);
            stat.setUpdatedAt(LocalDateTime.now());
            statMapper.insert(stat);
        }
        return stat;
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
