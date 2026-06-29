package com.studybuddy.checkin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CalendarResp {
    /** yyyy-MM */
    private String month;
    private List<CalendarDay> days;
}
