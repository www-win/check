package com.studybuddy.couple.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PokeResp {
    private Long id;
    private boolean fromMe;
    private String message;
    private LocalDateTime createdAt;
}
