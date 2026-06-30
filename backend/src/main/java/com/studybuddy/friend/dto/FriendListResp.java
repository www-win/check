package com.studybuddy.friend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class FriendListResp {
    private String myInviteCode;
    private List<FriendInfo> friends;
    private List<FriendRequestInfo> incoming;
    private List<FriendRequestInfo> outgoing;
}
