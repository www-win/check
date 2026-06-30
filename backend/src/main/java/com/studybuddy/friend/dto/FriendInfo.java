package com.studybuddy.friend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendInfo {
    private Long userId;
    private String nickname;
    private String avatar;
}
