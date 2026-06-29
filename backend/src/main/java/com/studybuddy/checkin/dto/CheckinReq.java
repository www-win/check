package com.studybuddy.checkin.dto;

import lombok.Data;

@Data
public class CheckinReq {
    /** 心情 1-5，可空 */
    private Integer mood;
    /** 当日笔记，可空 */
    private String note;
    /** 打卡照片 URL（先调 /api/checkin/upload 获取），可空 */
    private String imageUrl;
}
