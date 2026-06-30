package com.studybuddy.friend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("friendship")
public class Friendship {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long requesterId;
    private Long addresseeId;
    private Integer status; // 0=待确认 1=已成为好友
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
}
