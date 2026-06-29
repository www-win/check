package com.studybuddy.checkin.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.studybuddy.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 阿里云 OSS 实现：代理上传，返回公网 URL。 */
@Component
@RequiredArgsConstructor
public class OssImageStorage implements ImageStorage {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final OssProps props;
    private volatile OSS client;

    @Override
    public String upload(Long userId, byte[] data, String ext) {
        if (props.getEndpoint() == null || props.getEndpoint().isBlank()
                || props.getBucket() == null || props.getBucket().isBlank()) {
            throw new BizException(50001, "对象存储未配置");
        }
        String key = "%s/%d/%s/%s.%s".formatted(
                props.getKeyPrefix(), userId, LocalDate.now().format(MONTH),
                UUID.randomUUID().toString().replace("-", ""), ext);
        try {
            oss().putObject(props.getBucket(), key, new ByteArrayInputStream(data));
        } catch (Exception e) {
            throw new BizException(50001, "图片上传失败");
        }
        String prefix = props.getUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "https://%s.%s".formatted(props.getBucket(), props.getEndpoint());
        }
        return prefix + "/" + key;
    }

    private OSS oss() {
        OSS c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = new OSSClientBuilder().build(
                            props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
                    client = c;
                }
            }
        }
        return c;
    }
}
