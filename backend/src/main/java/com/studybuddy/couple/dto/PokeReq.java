package com.studybuddy.couple.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PokeReq {
    @Size(max = 200, message = "留言最多 200 字")
    private String message;
}
