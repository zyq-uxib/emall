SET NAMES utf8mb4;

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS `mall_crawler_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `mall_crawler_db`;

-- 2. 系统管理员表
DROP TABLE IF EXISTS `t_admin`;
CREATE TABLE `t_admin` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` VARCHAR(64) NOT NULL UNIQUE COMMENT '登录账号',
  `password` VARCHAR(128) NOT NULL COMMENT '登录密码',
  `nickname` VARCHAR(64) DEFAULT 'Admin' COMMENT '显示昵称',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统管理员表';

-- 3. 商品分类表
DROP TABLE IF EXISTS `t_category`;
CREATE TABLE `t_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `category_name` VARCHAR(64) NOT NULL UNIQUE COMMENT '分类名称',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- 4. 商品信息主表
DROP TABLE IF EXISTS `t_product`;
CREATE TABLE `t_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id` BIGINT NOT NULL COMMENT '所属分类ID',
  `title` VARCHAR(255) NOT NULL COMMENT '商品标题',
  `price` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 COMMENT '商品价格',
  `stock` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
  `rating` INT NOT NULL DEFAULT 5 COMMENT '推荐星级(1-5)',
  `image_url` VARCHAR(512) DEFAULT '' COMMENT '商品封面图URL',
  `source_url` VARCHAR(512) NOT NULL UNIQUE COMMENT '源站详情页URL(用于去重)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-上架, 0-下架',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_title` (`title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电商商品主表';

-- 5. 爬虫运行日志表
DROP TABLE IF EXISTS `t_crawl_log`;
CREATE TABLE `t_crawl_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `target_url` VARCHAR(255) NOT NULL COMMENT '爬取目标网址',
  `success_count` INT NOT NULL DEFAULT 0 COMMENT '成功入库条数',
  `status` VARCHAR(255) NOT NULL COMMENT '执行状态',
  `cost_time_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '耗时(毫秒)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫任务日志表';

-- 6. 初始化基础数据
INSERT IGNORE INTO `t_category` (`category_name`) VALUES ('General');
INSERT IGNORE INTO `t_admin` (`username`, `password`, `nickname`) VALUES ('your_admin_user', 'change_me_on_deploy', '超级管理员');