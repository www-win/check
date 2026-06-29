-- 学伴打卡 数据库结构（MySQL 8）
-- 手动执行：mysql -u root -p studybuddy < schema.sql
-- 先建库：CREATE DATABASE IF NOT EXISTS studybuddy DEFAULT CHARSET utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `phone`      VARCHAR(20)  NULL,
  `openid`     VARCHAR(64)  NULL,
  `nickname`   VARCHAR(50)  NULL,
  `avatar`     VARCHAR(255) NULL,
  `invite_code` VARCHAR(20) NULL,
  `plan`       VARCHAR(20)  NULL,
  `created_at` DATETIME     NULL,
  `updated_at` DATETIME     NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `checkin_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT       NOT NULL,
  `checkin_date`  DATE         NOT NULL,
  `type`          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=正常签到 1=补卡',
  `mood`          TINYINT      NULL COMMENT '心情 1-5',
  `note`          VARCHAR(200) NULL,
  `image_url`     VARCHAR(500) NULL,
  `points_earned` INT          NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `checkin_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `study_goal` (
  `user_id`     BIGINT       NOT NULL,
  `content`     VARCHAR(200) NOT NULL,
  `target_date` DATE         NULL,
  `updated_at`  DATETIME     NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `checkin_stat` (
  `user_id`           BIGINT   NOT NULL,
  `current_streak`    INT      NOT NULL DEFAULT 0,
  `max_streak`        INT      NOT NULL DEFAULT 0,
  `total_days`        INT      NOT NULL DEFAULT 0,
  `last_checkin_date` DATE     NULL,
  `points`            INT      NOT NULL DEFAULT 0,
  `updated_at`        DATETIME NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
