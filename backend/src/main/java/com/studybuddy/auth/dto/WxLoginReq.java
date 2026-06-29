package com.studybuddy.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginReq {
    @NotBlank(message = "缺少 code")
    private String code;
}
