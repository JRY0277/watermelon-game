-- MySQL / Navicat 版建表语句
-- 说明：当前后端用的是 SQLite（零依赖，开箱即用）。
-- 等能联网装上 pymysql 后，把这套表建到你的 MySQL 里即可切换。

CREATE DATABASE IF NOT EXISTS merge_game
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE merge_game;

CREATE TABLE IF NOT EXISTS scores (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  nickname   VARCHAR(12)  NOT NULL DEFAULT '匿名玩家',
  score      INT          NOT NULL DEFAULT 0,
  max_level  TINYINT      NOT NULL DEFAULT 0 COMMENT '最高合到第几级',
  duration   INT          NOT NULL DEFAULT 0 COMMENT '本局时长(秒)',
  created_at DATETIME     NOT NULL,
  INDEX idx_score (score DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 可选：图片套餐分享（把一套图存成一条记录，别人用口令加载）
CREATE TABLE IF NOT EXISTS image_packs (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(16)  NOT NULL COMMENT '分享口令',
  name       VARCHAR(40)  NOT NULL DEFAULT '',
  levels     TINYINT      NOT NULL DEFAULT 0 COMMENT '图片张数',
  images     LONGTEXT     NOT NULL COMMENT 'JSON 数组，base64 图片',
  created_at DATETIME     NOT NULL,
  UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 常用查询
-- SELECT nickname, score, max_level, created_at FROM scores ORDER BY score DESC LIMIT 20;
-- SELECT COUNT(*) 局数, COUNT(DISTINCT nickname) 人数, MAX(score) 最高分, AVG(score) 平均分 FROM scores;
