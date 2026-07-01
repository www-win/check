package com.studybuddy.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ChatMessageInfo {
    private Long id;
    private boolean mine;
    private String content;
    private LocalDateTime createdAt;
}
