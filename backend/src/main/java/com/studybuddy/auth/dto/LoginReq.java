package com.studybuddy.auth.dto;

import lombok.Data;

@Data
public class LoginReq {
    private String phone;
    private String code;
}
