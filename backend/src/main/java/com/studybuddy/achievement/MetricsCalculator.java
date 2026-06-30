package com.studybuddy.achievement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.checkin.entity.CheckinRecord;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import com.studybuddy.couple.entity.Couple;
import com.studybuddy.couple.mapper.CoupleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetricsCalculator {
    private final CheckinStatMapper statMapper;
    private final CheckinRecordMapper recordMapper;
    private final CoupleMapper coupleMapper;

    public AchievementMetrics compute(Long userId) {
        AchievementMetrics m = new AchievementMetrics();

        CheckinStat stat = statMapper.selectById(userId);
        m.maxStreak = stat == null ? 0 : n(stat.getMaxStreak());
        m.totalDays = stat == null ? 0 : n(stat.getTotalDays());

        m.hasPhoto = recordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .isNotNull(CheckinRecord::getImageUrl)) > 0;
        m.hasNote = recordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .isNotNull(CheckinRecord::getNote)) > 0;
        long moods = recordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .isNotNull(CheckinRecord::getMood));
        m.moodCount = (int) moods;

        Couple couple = coupleMapper.selectOne(new LambdaQueryWrapper<Couple>()
                .eq(Couple::getStatus, 1)
                .and(w -> w.eq(Couple::getRequesterId, userId).or().eq(Couple::getTargetId, userId))
                .last("limit 1"));
        if (couple != null) {
            m.coupleBound = true;
            Long pid = couple.getRequesterId().equals(userId)
                    ? couple.getTargetId() : couple.getRequesterId();
            m.commonDays = recordMapper.countCommonDays(userId, pid);
        }
        return m;
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
