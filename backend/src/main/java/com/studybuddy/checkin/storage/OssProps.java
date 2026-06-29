package com.studybuddy.checkin.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 对象存储配置，对应 application.yml 的 studybuddy.oss。 */
@Data
@Component
@ConfigurationProperties(prefix = "studybuddy.oss")
public class OssProps {
    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    /** 公网访问前缀（CDN 或 bucket 域名），不含末尾斜杠 */
    private String urlPrefix;
    /** 对象键前缀目录 */
    private String keyPrefix = "checkin";
}
