-- 情侣关系功能 数据库迁移（在已有 studybuddy 库上单独执行）
-- 执行：mysql -u <user> -p studybuddy < migration-couple.sql
-- 幂等：表用 CREATE TABLE IF NOT EXISTS，索引用存储过程守卫，可安全重复执行。
-- 前置检查（仅当 user 表已有重复的非空 invite_code 时索引会失败，正常不会发生）：
--   SELECT invite_code, COUNT(*) c FROM `user`
--   WHERE invite_code IS NOT NULL AND invite_code <> ''
--   GROUP BY invite_code HAVING c > 1;

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

-- 邀请码唯一索引（情侣绑定用）：幂等添加，重复执行不报错
DROP PROCEDURE IF EXISTS `add_uk_invite_code`;
DELIMITER $$
CREATE PROCEDURE `add_uk_invite_code`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE table_schema = DATABASE()
      AND table_name = 'user'
      AND index_name = 'uk_invite_code'
  ) THEN
    ALTER TABLE `user` ADD UNIQUE KEY `uk_invite_code` (`invite_code`);
  END IF;
END$$
DELIMITER ;
CALL `add_uk_invite_code`();
DROP PROCEDURE IF EXISTS `add_uk_invite_code`;
