package com.studybuddy;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class StudybuddyApplication {
    // 云托管容器 JVM 默认时区为 UTC，LocalDateTime.now() 会比北京时间少 8 小时(分钟一致、小时错)。
    // 强制进程默认时区为东八区，让全站 now() 生成北京时间。
    @PostConstruct
    public void initTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    public static void main(String[] args) {
        SpringApplication.run(StudybuddyApplication.class, args);
    }
}
