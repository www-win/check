package com.studybuddy.checkin.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MakeupReq {
    /** 要补卡的日期（过去 7 天内、未签到） */
    @NotNull(message = "缺少补卡日期")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    private Integer mood;
    private String note;
    private String imageUrl;
}
