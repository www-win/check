package com.studybuddy.couple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("couple_poke")
public class CouplePoke {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long coupleId;
    private Long fromUser;
    private Long toUser;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
