package com.studybuddy.couple.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoupleStatusResp {
    private String status;        // NONE / PENDING_OUT / PENDING_IN / ACTIVE
    private String myInviteCode;  // 始终返回，方便分享
    private Long coupleId;        // ACTIVE 时有值
    private PartnerInfo partner;  // PENDING_*/ACTIVE 时有值
    private long unreadPokeCount; // ACTIVE 时对方戳我的未读数

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnerInfo {
        private String nickname;
        private String avatar;
    }
}
