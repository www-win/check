package com.studybuddy.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("checkin_stat")
public class CheckinStat {
    /** 主键即 user_id，由业务写入（非自增）。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private Integer currentStreak;
    private Integer maxStreak;
    private Integer totalDays;
    private LocalDate lastCheckinDate;
    private Integer points;
    private LocalDateTime updatedAt;
}
