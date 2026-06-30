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
  UNIQUE KEY `uk_openid` (`openid`),
  UNIQUE KEY `uk_invite_code` (`invite_code`)
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

-- 情侣关系
CREATE TABLE IF NOT EXISTS `couple` (
  `id`           BIGINT   NOT NULL AUTO_INCREMENT,
  `requester_id` BIGINT   NOT NULL COMMENT '发起绑定方（输入了对方邀请码）',
  `target_id`    BIGINT   NOT NULL COMMENT '邀请码拥有方（待同意方）',
  `status`       TINYINT  NOT NULL COMMENT '0=待确认 1=已建立',
  `created_at`   DATETIME NOT NULL,
  `confirmed_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_requester` (`requester_id`),
  KEY `idx_target` (`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 情侣互动（戳一下/留言）
CREATE TABLE IF NOT EXISTS `couple_poke` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `couple_id`  BIGINT   NOT NULL,
  `from_user`  BIGINT   NOT NULL,
  `to_user`    BIGINT   NOT NULL,
  `message`    VARCHAR(200) NULL COMMENT '空=纯戳一下，有值=留言督促',
  `created_at` DATETIME NOT NULL,
  `read_at`    DATETIME NULL COMMENT '对方已读时间',
  PRIMARY KEY (`id`),
  KEY `idx_couple` (`couple_id`),
  KEY `idx_to_unread` (`to_user`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 邀请码唯一索引（情侣绑定用）：新库由上方 user 建表语句自带 uk_invite_code。
-- 已存在的老库需补加该索引，请单独执行 db/migration-couple.sql（mysql 命令行客户端）。
-- 注意：本文件被 Spring sql.init（mode=always）每次启动执行，只能放逐句、幂等、
-- 不含 DELIMITER/存储过程 的语句，否则会导致应用启动失败。
