package com.studybuddy.checkin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studybuddy.checkin.dto.CalendarDay;
import com.studybuddy.checkin.dto.CalendarResp;
import com.studybuddy.checkin.dto.CheckinReq;
import com.studybuddy.checkin.dto.CheckinResp;
import com.studybuddy.checkin.dto.CheckinStatusResp;
import com.studybuddy.checkin.dto.MakeupReq;
import com.studybuddy.checkin.entity.CheckinRecord;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import com.studybuddy.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.studybuddy.checkin.dto.HeatmapResp;
import com.studybuddy.checkin.dto.HeatmapSummary;

@Service
@RequiredArgsConstructor
public class CheckinService {
    private final CheckinRecordMapper recordMapper;
    private final CheckinStatMapper statMapper;
    private final StreakCalculator streakCalculator;
    private final CheckinProps props;

    /** 今日签到。 */
    @Transactional
    public CheckinResp checkin(Long userId, CheckinReq req) {
        LocalDate today = LocalDate.now();
        if (findRecord(userId, today) != null) {
            throw new BizException(40012, "今天已签到");
        }
        CheckinStat stat = ensureStat(userId);

        CheckinRecord record = newRecord(userId, today, 0, req.getMood(), req.getNote(), req.getImageUrl());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BizException(40012, "今天已签到");
        }

        recompute(userId, stat);

        int streak = stat.getCurrentStreak();
        Integer milestone = null;
        int earned = props.getBasePoints();
        Integer bonus = props.getMilestones().get(streak);
        if (bonus != null) {
            earned += bonus;
            milestone = streak;
        }
        record.setPointsEarned(earned);
        recordMapper.updateById(record);

        stat.setPoints(n(stat.getPoints()) + earned);
        stat.setUpdatedAt(LocalDateTime.now());
        statMapper.updateById(stat);

        return new CheckinResp(stat.getCurrentStreak(), stat.getMaxStreak(),
                stat.getTotalDays(), earned, stat.getPoints(), milestone);
    }

    /** 今日状态。 */
    public CheckinStatusResp status(Long userId) {
        boolean todayChecked = findRecord(userId, LocalDate.now()) != null;
        CheckinStat stat = statMapper.selectById(userId);
        if (stat == null) {
            return new CheckinStatusResp(todayChecked, 0, 0, 0, 0);
        }
        return new CheckinStatusResp(todayChecked, n(stat.getCurrentStreak()),
                n(stat.getMaxStreak()), n(stat.getTotalDays()), n(stat.getPoints()));
    }

    /** 当月签到日历。month 格式 yyyy-MM。 */
    public CalendarResp calendar(Long userId, String month) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(month);
        } catch (Exception e) {
            throw new BizException(40000, "month 格式应为 yyyy-MM");
        }
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        Map<LocalDate, CheckinRecord> map = recordMapper.selectList(new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getUserId, userId)
                        .ge(CheckinRecord::getCheckinDate, first)
                        .le(CheckinRecord::getCheckinDate, last))
                .stream()
                .collect(Collectors.toMap(CheckinRecord::getCheckinDate, Function.identity(), (a, b) -> a));

        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            CheckinRecord r = map.get(d);
            if (r == null) {
                days.add(new CalendarDay(d, 0, null, null));
            } else {
                int status = (r.getType() != null && r.getType() == 1) ? 2 : 1;
                days.add(new CalendarDay(d, status, r.getMood(), r.getImageUrl()));
            }
        }
        return new CalendarResp(month, days);
    }

    /** 年度打卡热力图数据。返回该年签到/补卡日期数组 + 汇总（纯只读）。 */
    public HeatmapResp heatmap(Long userId, int year) {
        if (year < 2000 || year > 2200) {
            throw new BizException(40000, "year 不合法");
        }
        LocalDate first = LocalDate.of(year, 1, 1);
        LocalDate last = LocalDate.of(year, 12, 31);
        List<CheckinRecord> records = recordMapper.selectList(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .ge(CheckinRecord::getCheckinDate, first)
                .le(CheckinRecord::getCheckinDate, last));

        List<String> signed = new ArrayList<>();
        List<String> makeup = new ArrayList<>();
        Set<LocalDate> all = new HashSet<>();
        for (CheckinRecord r : records) {
            LocalDate d = r.getCheckinDate();
            all.add(d);
            if (r.getType() != null && r.getType() == 1) {
                makeup.add(d.toString());
            } else {
                signed.add(d.toString());
            }
        }
        int totalDays = all.size();
        int longestStreak = streakCalculator.longestStreak(all);
        int rate = streakCalculator.rate(totalDays, year, LocalDate.now());
        HeatmapSummary summary = new HeatmapSummary(totalDays, longestStreak, makeup.size(), rate);
        return new HeatmapResp(year, signed, makeup, summary);
    }

    /** 补卡：补过去 makeupWindowDays 天内、未签到的日期，消耗积分。 */
    @Transactional
    public CheckinStatusResp makeup(Long userId, MakeupReq req) {
        LocalDate date = req.getDate();
        LocalDate today = LocalDate.now();
        if (!date.isBefore(today)) {
            throw new BizException(40011, "只能补过去的日期");
        }
        if (date.isBefore(today.minusDays(props.getMakeupWindowDays()))) {
            throw new BizException(40011, "超出可补卡时间范围");
        }
        if (findRecord(userId, date) != null) {
            throw new BizException(40012, "该日已签到");
        }
        CheckinStat stat = ensureStat(userId);
        if (n(stat.getPoints()) < props.getMakeupCost()) {
            throw new BizException(40010, "积分不足，无法补卡");
        }

        CheckinRecord record = newRecord(userId, date, 1, req.getMood(), req.getNote(), req.getImageUrl());
        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BizException(40012, "该日已签到");
        }

        stat.setPoints(n(stat.getPoints()) - props.getMakeupCost());
        recompute(userId, stat);
        stat.setUpdatedAt(LocalDateTime.now());
        statMapper.updateById(stat);

        boolean todayChecked = findRecord(userId, today) != null;
        return new CheckinStatusResp(todayChecked, stat.getCurrentStreak(),
                stat.getMaxStreak(), stat.getTotalDays(), stat.getPoints());
    }

    /** 撤销今日的正常打卡:删除记录、退回积分、重算连续/累计天数。 */
    @Transactional
    public CheckinStatusResp cancelToday(Long userId) {
        LocalDate today = LocalDate.now();
        CheckinRecord record = findRecord(userId, today);
        if (record == null) {
            throw new BizException(40013, "今天还没打卡");
        }
        if (record.getType() != null && record.getType() == 1) {
            throw new BizException(40014, "补卡记录不支持撤销");
        }

        CheckinStat stat = ensureStat(userId);
        int refunded = n(record.getPointsEarned());
        stat.setPoints(Math.max(0, n(stat.getPoints()) - refunded));

        recordMapper.deleteById(record.getId());

        recompute(userId, stat);
        stat.setUpdatedAt(LocalDateTime.now());
        statMapper.updateById(stat);

        return new CheckinStatusResp(false, stat.getCurrentStreak(),
                stat.getMaxStreak(), stat.getTotalDays(), stat.getPoints());
    }

    // ---- 内部 ----

    /** 从 last_checkin_date 往回重算连续天数、累计天数（就地修改 stat，不持久化）。 */
    private void recompute(Long userId, CheckinStat stat) {
        LocalDate anchor = maxCheckinDate(userId);
        if (anchor == null) {
            stat.setCurrentStreak(0);
            stat.setTotalDays(0);
            stat.setLastCheckinDate(null);
            return;
        }
        LocalDate from = anchor.minusDays(props.getStreakWindowDays());
        Set<LocalDate> dates = recordMapper.selectList(new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getUserId, userId)
                        .ge(CheckinRecord::getCheckinDate, from)
                        .le(CheckinRecord::getCheckinDate, anchor))
                .stream().map(CheckinRecord::getCheckinDate).collect(Collectors.toSet());
        int streak = streakCalculator.currentStreak(dates, anchor);
        long total = recordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId));

        stat.setCurrentStreak(streak);
        stat.setMaxStreak(Math.max(n(stat.getMaxStreak()), streak));
        stat.setTotalDays((int) total);
        stat.setLastCheckinDate(anchor);
    }

    private LocalDate maxCheckinDate(Long userId) {
        CheckinRecord r = recordMapper.selectOne(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .orderByDesc(CheckinRecord::getCheckinDate)
                .last("limit 1"));
        return r == null ? null : r.getCheckinDate();
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

    private CheckinRecord findRecord(Long userId, LocalDate date) {
        return recordMapper.selectOne(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getUserId, userId)
                .eq(CheckinRecord::getCheckinDate, date));
    }

    private CheckinRecord newRecord(Long userId, LocalDate date, int type,
                                    Integer mood, String note, String imageUrl) {
        CheckinRecord r = new CheckinRecord();
        r.setUserId(userId);
        r.setCheckinDate(date);
        r.setType(type);
        r.setMood(mood);
        r.setNote(note);
        r.setImageUrl(imageUrl);
        r.setPointsEarned(0);
        r.setCreatedAt(LocalDateTime.now());
        return r;
    }

    private static int n(Integer v) {
        return v == null ? 0 : v;
    }
}
