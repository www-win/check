package com.studybuddy.checkin;

import com.studybuddy.checkin.dto.CheckinStatusResp;
import com.studybuddy.checkin.dto.MakeupReq;
import com.studybuddy.checkin.entity.CheckinRecord;
import com.studybuddy.checkin.entity.CheckinStat;
import com.studybuddy.checkin.mapper.CheckinRecordMapper;
import com.studybuddy.checkin.mapper.CheckinStatMapper;
import com.studybuddy.common.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    @Mock
    CheckinRecordMapper recordMapper;
    @Mock
    CheckinStatMapper statMapper;
    @Spy
    StreakCalculator streakCalculator = new StreakCalculator();
    @Spy
    CheckinProps props = defaultProps();

    @InjectMocks
    CheckinService service;

    private static CheckinProps defaultProps() {
        CheckinProps p = new CheckinProps();
        p.setBasePoints(10);
        p.setMakeupCost(50);
        p.setMakeupWindowDays(7);
        p.setStreakWindowDays(90);
        return p;
    }

    private final Long uid = 1L;

    @BeforeEach
    void setUp() {
        // 默认无 stat（个别用例覆盖）
        lenient().when(statMapper.selectById(uid)).thenReturn(null);
    }

    @Test
    void duplicateCheckinThrows40012() {
        CheckinRecord today = new CheckinRecord();
        today.setCheckinDate(LocalDate.now());
        when(recordMapper.selectOne(any())).thenReturn(today);

        BizException e = assertThrows(BizException.class,
                () -> service.checkin(uid, new com.studybuddy.checkin.dto.CheckinReq()));
        assertEquals(40012, e.getCode());
    }

    @Test
    void makeupFutureDateThrows40011() {
        MakeupReq req = new MakeupReq();
        req.setDate(LocalDate.now().plusDays(1));

        BizException e = assertThrows(BizException.class, () -> service.makeup(uid, req));
        assertEquals(40011, e.getCode());
    }

    @Test
    void makeupTooOldThrows40011() {
        MakeupReq req = new MakeupReq();
        req.setDate(LocalDate.now().minusDays(8)); // 窗口 7 天，第 8 天超窗

        BizException e = assertThrows(BizException.class, () -> service.makeup(uid, req));
        assertEquals(40011, e.getCode());
    }

    @Test
    void makeupOnAlreadyCheckedDateThrows40012() {
        MakeupReq req = new MakeupReq();
        req.setDate(LocalDate.now().minusDays(1));
        CheckinRecord existing = new CheckinRecord();
        existing.setCheckinDate(req.getDate());
        when(recordMapper.selectOne(any())).thenReturn(existing);

        BizException e = assertThrows(BizException.class, () -> service.makeup(uid, req));
        assertEquals(40012, e.getCode());
    }

    @Test
    void makeupInsufficientPointsThrows40010() {
        MakeupReq req = new MakeupReq();
        req.setDate(LocalDate.now().minusDays(1));
        when(recordMapper.selectOne(any())).thenReturn(null); // 该日未签

        CheckinStat stat = new CheckinStat();
        stat.setUserId(uid);
        stat.setPoints(10); // 不足 50
        stat.setCurrentStreak(0);
        stat.setMaxStreak(0);
        stat.setTotalDays(0);
        when(statMapper.selectById(uid)).thenReturn(stat);

        BizException e = assertThrows(BizException.class, () -> service.makeup(uid, req));
        assertEquals(40010, e.getCode());
    }

    @Test
    void cancelTodayRefundsPointsAndDeletesRecord() {
        CheckinRecord today = new CheckinRecord();
        today.setId(99L);
        today.setUserId(uid);
        today.setCheckinDate(LocalDate.now());
        today.setType(0);
        today.setPointsEarned(10);
        when(recordMapper.selectOne(any())).thenReturn(today);

        CheckinStat stat = new CheckinStat();
        stat.setUserId(uid);
        stat.setPoints(30);
        stat.setCurrentStreak(3);
        stat.setMaxStreak(5);
        stat.setTotalDays(3);
        when(statMapper.selectById(uid)).thenReturn(stat);
        // recompute 内部查询：无剩余记录 → 连续/累计归零
        when(recordMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(recordMapper.selectCount(any())).thenReturn(0L);

        CheckinStatusResp resp = service.cancelToday(uid);

        org.mockito.Mockito.verify(recordMapper).deleteById(99L);
        assertEquals(false, resp.isTodayChecked());
        assertEquals(20, resp.getPoints());   // 30 - 10
        assertEquals(5, resp.getMaxStreak()); // 历史最高不回退
        assertEquals(0, resp.getTotalDays());
        assertEquals(0, resp.getCurrentStreak());
    }

    @Test
    void cancelTodayWhenNotCheckedThrows40013() {
        when(recordMapper.selectOne(any())).thenReturn(null);

        BizException e = assertThrows(BizException.class, () -> service.cancelToday(uid));
        assertEquals(40013, e.getCode());
    }

    @Test
    void cancelTodayMakeupRecordThrows40014() {
        CheckinRecord today = new CheckinRecord();
        today.setId(99L);
        today.setCheckinDate(LocalDate.now());
        today.setType(1); // 补卡
        when(recordMapper.selectOne(any())).thenReturn(today);

        BizException e = assertThrows(BizException.class, () -> service.cancelToday(uid));
        assertEquals(40014, e.getCode());
    }
}
