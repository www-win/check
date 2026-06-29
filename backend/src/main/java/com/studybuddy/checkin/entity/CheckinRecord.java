package com.studybuddy.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("checkin_record")
public class CheckinRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate checkinDate;
    /** 0=正常签到 1=补卡 */
    private Integer type;
    private Integer mood;
    private String note;
    private String imageUrl;
    private Integer pointsEarned;
    private LocalDateTime createdAt;
}
