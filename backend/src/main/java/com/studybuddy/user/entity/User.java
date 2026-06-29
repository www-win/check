package com.studybuddy.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String openid;
    private String nickname;
    private String avatar;
    private String inviteCode;
    private String plan;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
