package com.studybuddy.checkin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class CalendarDay {
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    /** 0=未签 1=正常签到 2=补卡 */
    private int status;
    private Integer mood;
    private String imageUrl;
}
