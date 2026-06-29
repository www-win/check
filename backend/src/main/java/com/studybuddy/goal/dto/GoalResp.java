package com.studybuddy.goal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class GoalResp {
    private String content;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;
}
