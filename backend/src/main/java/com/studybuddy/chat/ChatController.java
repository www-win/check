package com.studybuddy.chat;

import com.studybuddy.chat.dto.ChatMessageInfo;
import com.studybuddy.chat.dto.ConversationInfo;
import com.studybuddy.chat.dto.ReadReq;
import com.studybuddy.chat.dto.SendMsgReq;
import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    @GetMapping("/conversations")
    public R<List<ConversationInfo>> conversations() {
        return R.ok(chatService.conversations(CurrentUser.get()));
    }

    @GetMapping("/messages")
    public R<List<ChatMessageInfo>> messages(@RequestParam Long peerId,
                                             @RequestParam(required = false) Long afterId,
                                             @RequestParam(required = false) Integer limit) {
        return R.ok(chatService.messages(CurrentUser.get(), peerId, afterId, limit));
    }

    @PostMapping("/messages")
    public R<ChatMessageInfo> send(@RequestBody SendMsgReq req) {
        return R.ok(chatService.send(CurrentUser.get(), req.getPeerId(), req.getContent()));
    }

    @PostMapping("/read")
    public R<Void> read(@RequestBody ReadReq req) {
        chatService.markRead(CurrentUser.get(), req.getPeerId());
        return R.ok();
    }
}
