package com.studybuddy.couple.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindReq {
    @NotBlank(message = "请输入邀请码")
    private String inviteCode;
}
