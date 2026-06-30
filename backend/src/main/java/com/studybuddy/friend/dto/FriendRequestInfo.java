package com.studybuddy.friend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendRequestInfo {
    private Long requestId;
    private Long userId;
    private String nickname;
    private String avatar;
}
