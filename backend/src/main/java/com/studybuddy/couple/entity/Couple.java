package com.studybuddy.couple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("couple")
public class Couple {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long targetId;
    private Integer status; // 0=待确认 1=已建立
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}
