package com.studybuddy.chat.dto;

import lombok.Data;

@Data
public class SendMsgReq {
    private Long peerId;
    private String content;
}
