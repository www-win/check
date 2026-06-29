package com.studybuddy.goal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class GoalReq {
    @NotBlank(message = "目标内容不能为空")
    @Size(max = 200, message = "目标内容不能超过 200 字")
    private String content;

    /** 目标日期，可空 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate targetDate;
}
