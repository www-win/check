package com.studybuddy.checkin;

import com.studybuddy.checkin.dto.CalendarResp;
import com.studybuddy.checkin.dto.CheckinReq;
import com.studybuddy.checkin.dto.CheckinResp;
import com.studybuddy.checkin.dto.CheckinStatusResp;
import com.studybuddy.checkin.dto.MakeupReq;
import com.studybuddy.checkin.dto.UploadResp;
import com.studybuddy.checkin.storage.ImageStorage;
import com.studybuddy.common.BizException;
import com.studybuddy.common.CurrentUser;
import com.studybuddy.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");

    private final CheckinService checkinService;
    private final ImageStorage imageStorage;

    @PostMapping
    public R<CheckinResp> checkin(@RequestBody CheckinReq req) {
        return R.ok(checkinService.checkin(CurrentUser.get(), req));
    }

    @GetMapping("/status")
    public R<CheckinStatusResp> status() {
        return R.ok(checkinService.status(CurrentUser.get()));
    }

    @PostMapping("/cancel")
    public R<CheckinStatusResp> cancel() {
        return R.ok(checkinService.cancelToday(CurrentUser.get()));
    }

    @GetMapping("/calendar")
    public R<CalendarResp> calendar(@RequestParam String month) {
        return R.ok(checkinService.calendar(CurrentUser.get(), month));
    }

    @PostMapping("/makeup")
    public R<CheckinStatusResp> makeup(@Valid @RequestBody MakeupReq req) {
        return R.ok(checkinService.makeup(CurrentUser.get(), req));
    }

    @PostMapping("/upload")
    public R<UploadResp> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(40020, "文件为空");
        }
        String ext = resolveExt(file.getOriginalFilename());
        try {
            String url = imageStorage.upload(CurrentUser.get(), file.getBytes(), ext);
            return R.ok(new UploadResp(url));
        } catch (IOException e) {
            throw new BizException(40020, "文件读取失败");
        }
    }

    private String resolveExt(String filename) {
        String ext = null;
        if (filename != null && filename.contains(".")) {
            ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            throw new BizException(40020, "仅支持 jpg/png/webp 格式");
        }
        return ext;
    }
}
