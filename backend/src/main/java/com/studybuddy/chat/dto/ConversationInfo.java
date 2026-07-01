package com.studybuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ConversationInfo {
    private Long peerUserId;
    private String nickname;
    private String avatar;
    private String lastContent;
    private LocalDateTime lastTime;
    private long unread;
}
