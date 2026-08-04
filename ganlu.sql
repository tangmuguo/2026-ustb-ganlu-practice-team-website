/*
 Navicat Premium Data Transfer

 Source Server         : sanitized-export
 Source Server Type    : MySQL
 Source Server Version : 80043
 Source Host           : removed
 Source Schema         : ganlu

 Target Server Type    : MySQL
 Target Server Version : 80043
 File Encoding         : 65001

 Date: 26/08/2025 20:55:27
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for banner
-- ----------------------------
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '轮播图标题',
  `imageUrl` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '图片URL',
  `linkUrl` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '点击跳转链接',
  `sortOrder` int(0) NULL DEFAULT 0 COMMENT '排序权重(数字越大越靠前)',
  `isVisible` tinyint(1) NULL DEFAULT 1 COMMENT '是否显示(0:隐藏,1:显示)',
  `createdAt` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatedAt` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '轮播图表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for course
-- ----------------------------
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `course_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '课程分类名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for course_detail
-- ----------------------------
DROP TABLE IF EXISTS `course_detail`;
CREATE TABLE `course_detail`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标题',
  `author` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '上传者',
  `thumbnail_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '缩略图',
  `course_id` int(0) NULL DEFAULT NULL COMMENT '科目id',
  `files` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件',
  `create_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `file_size` bigint(0) NULL DEFAULT NULL COMMENT '文件大小(字节)',
  `file_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '文件类型',
  `courseType` int(0) NULL DEFAULT NULL COMMENT '课程类型 1通识课程 2特色课程',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `course_id`(`course_id`) USING BTREE,
  CONSTRAINT `course_detail_ibfk_1` FOREIGN KEY (`course_id`) REFERENCES `course` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 86 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `user_id` int(0) NOT NULL COMMENT '留言用户ID',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '留言内容',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_message_status_time`(`status`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for news
-- ----------------------------
DROP TABLE IF EXISTS `news`;
CREATE TABLE `news`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `caption` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL COMMENT '内容',
  `createAt` datetime(0) NULL DEFAULT NULL COMMENT '创建时间',
  `imageUrl` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '封面',
  `linkUrl` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '跳转链接',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for reply
-- ----------------------------
DROP TABLE IF EXISTS `reply`;
CREATE TABLE `reply`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `message_id` int(0) NOT NULL COMMENT '关联的留言ID',
  `user_id` int(0) NOT NULL COMMENT '回复用户ID',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '回复内容',
  `create_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：1-正常，0-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_message_id`(`message_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE,
  INDEX `idx_reply_message_status_time`(`message_id`, `status`, `create_time`, `id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for team
-- ----------------------------
DROP TABLE IF EXISTS `team`;
CREATE TABLE `team`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `year` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '年份',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '团队名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for team_page
-- ----------------------------
DROP TABLE IF EXISTS `team_page`;
CREATE TABLE `team_page`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '修改时间',
  `status` enum('草稿','展示','归档') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '草稿' COMMENT '页面状态',
  `userId` int(0) NULL DEFAULT NULL COMMENT '队伍id',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `status`(`status`) USING BTREE,
  INDEX `team_id`(`userId`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for team_page_images
-- ----------------------------
DROP TABLE IF EXISTS `team_page_images`;
CREATE TABLE `team_page_images`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `userId` int(0) NULL DEFAULT NULL COMMENT '所属的用户id',
  `pageId` int(0) NULL DEFAULT NULL,
  `imageUrl` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `caption` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '内容',
  `displayOrder` int(0) NULL DEFAULT 0,
  `createdAt` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '类型：1队员  2支教',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `page_id`(`pageId`) USING BTREE,
  INDEX `display_order`(`displayOrder`) USING BTREE,
  CONSTRAINT `team_page_images_ibfk_1` FOREIGN KEY (`pageId`) REFERENCES `team_page` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Tables for permanent public image quota
-- ----------------------------
DROP TABLE IF EXISTS `public_image_quota`;
CREATE TABLE `public_image_quota` (
  `owner_user_id` int NOT NULL COMMENT '上传账号ID',
  `used_file_count` int NOT NULL DEFAULT 0 COMMENT '已转正图片数',
  `used_bytes` bigint NOT NULL DEFAULT 0 COMMENT '已转正图片累计字节数',
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`owner_user_id`),
  CONSTRAINT `chk_public_image_quota_count` CHECK (`used_file_count` >= 0),
  CONSTRAINT `chk_public_image_quota_bytes` CHECK (`used_bytes` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='公共图片永久配额原子账本';

  DROP TABLE IF EXISTS `public_image_asset`;
  CREATE TABLE `public_image_asset` (
    `asset_id` bigint NOT NULL AUTO_INCREMENT COMMENT '稳定资源编号；文件移动时保持不变',
    `relative_path` varchar(512) NOT NULL COMMENT '相对上传根目录的文件路径',
  `owner_user_id` int NOT NULL COMMENT '上传账号ID',
  `file_size` bigint NOT NULL COMMENT '文件真实字节数；禁止用0代替未知大小',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`asset_id`),
    UNIQUE KEY `uk_public_image_asset_path` (`relative_path`),
  KEY `idx_public_image_asset_owner` (`owner_user_id`),
  CONSTRAINT `chk_public_image_asset_size` CHECK (`file_size` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已转正公共图片所有者与大小';

DROP TABLE IF EXISTS `team_media`;
CREATE TABLE `team_media` (
  `id` int NOT NULL AUTO_INCREMENT,
  `filename` varchar(255) NOT NULL,
  `relative_path` varchar(512) NOT NULL,
  `mime_type` varchar(100) DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT 0,
  `uploader_id` int DEFAULT NULL,
  `team_id` int DEFAULT NULL,
  `related_type` varchar(20) DEFAULT NULL,
  `related_id` int DEFAULT NULL,
  `status` enum('PENDING','PUBLISHED','REJECTED','ARCHIVED') DEFAULT 'PENDING',
  `reject_reason` varchar(512) DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_uploader_id` (`uploader_id`),
  KEY `idx_related` (`related_type`,`related_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队风采视频/附件表';

DROP TABLE IF EXISTS `team_media_quota`;
CREATE TABLE `team_media_quota` (
  `owner_user_id` int NOT NULL,
  `used_file_count` int NOT NULL DEFAULT 0,
  `used_bytes` bigint NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`owner_user_id`),
  CONSTRAINT `chk_team_media_quota_count` CHECK (`used_file_count` >= 0),
  CONSTRAINT `chk_team_media_quota_bytes` CHECK (`used_bytes` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队附件账号级原子配额账本';

DROP TABLE IF EXISTS `team_media_global_quota`;
CREATE TABLE `team_media_global_quota` (
  `singleton_id` tinyint NOT NULL,
  `used_file_count` int NOT NULL DEFAULT 0,
  `used_bytes` bigint NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`singleton_id`),
  CONSTRAINT `chk_team_media_global_singleton` CHECK (`singleton_id` = 1),
  CONSTRAINT `chk_team_media_global_count` CHECK (`used_file_count` >= 0),
  CONSTRAINT `chk_team_media_global_bytes` CHECK (`used_bytes` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队附件服务器级原子配额账本';

DROP TABLE IF EXISTS `team_media_upload_reservation`;
CREATE TABLE `team_media_upload_reservation` (
  `reservation_id` char(36) NOT NULL,
  `owner_user_id` int NOT NULL,
  `reserved_bytes` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `expires_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `released_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`reservation_id`),
  KEY `idx_team_media_upload_active` (`status`,`expires_at`),
  KEY `idx_team_media_upload_rate` (`owner_user_id`,`created_at`),
  CONSTRAINT `chk_team_media_upload_bytes` CHECK (`reserved_bytes` > 0),
  CONSTRAINT `chk_team_media_upload_status` CHECK (`status` IN ('ACTIVE','RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Multipart解析前跨实例在途容量与速率记录';

DROP TABLE IF EXISTS `file_deletion_task`;
CREATE TABLE `file_deletion_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_type` varchar(32) NOT NULL,
  `asset_id` bigint NOT NULL,
  `relative_path` varchar(512) NOT NULL,
  `owner_user_id` int NOT NULL,
  `file_size` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `retry_count` int NOT NULL DEFAULT 0,
  `last_error` varchar(1000) DEFAULT NULL,
  `next_retry_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_deletion_asset` (`asset_type`,`asset_id`),
  KEY `idx_file_deletion_retry` (`status`,`next_retry_at`),
  CONSTRAINT `chk_file_deletion_size` CHECK (`file_size` >= 0),
  CONSTRAINT `chk_file_deletion_retry_count` CHECK (`retry_count` >= 0),
  CONSTRAINT `chk_file_deletion_status` CHECK (`status` IN ('PENDING','FAILED')),
  CONSTRAINT `chk_file_deletion_type` CHECK (`asset_type` IN ('PUBLIC_IMAGE','TEAM_MEDIA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='可审计、可重试的文件删除 outbox';

-- ----------------------------
-- Table structure for team_page_word
-- ----------------------------
DROP TABLE IF EXISTS `team_page_word`;
CREATE TABLE `team_page_word`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `userid` int(0) NULL DEFAULT NULL COMMENT '所属的用户id',
  `pageId` int(0) NULL DEFAULT NULL,
  `videoUrl` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL,
  `thumbnailUrl` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '缩略图',
  `caption` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '标题',
  `content` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '内容',
  `duration` int(0) NULL DEFAULT NULL COMMENT '视频时长(秒)',
  `displayOrder` int(0) NULL DEFAULT 0,
  `createdAt` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP,
  `updatedAt` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `type` int(0) NULL DEFAULT NULL COMMENT '类型 3荣誉 4日志',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `page_id`(`pageId`) USING BTREE,
  INDEX `display_order`(`displayOrder`) USING BTREE,
  CONSTRAINT `team_page_word_ibfk_1` FOREIGN KEY (`pageId`) REFERENCES `team_page` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `imageUrl` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '封面图片',
  `teamname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '团队名称',
  `helplocation` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支教地',
  `helpschool` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '支教小学',
  `realname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '真名',
  `belongschool` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '所属小学',
  `grade` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '年级',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号',
  `level` int(0) NULL DEFAULT NULL COMMENT '权限等级 0管理员 1团队 2小学生',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE = InnoDB AUTO_INCREMENT = 25 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for volunteer_application
-- ----------------------------
DROP TABLE IF EXISTS `volunteer_application`;
CREATE TABLE `volunteer_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `organization` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `grade_or_major` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `preferred_region` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `skills` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL,
  `introduction` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  `privacy_agreed` tinyint(1) NOT NULL DEFAULT 0,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'PENDING',
  `active_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci GENERATED ALWAYS AS (CASE WHEN `status` IN ('PENDING','CONTACTED') THEN `phone` ELSE NULL END) STORED,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_volunteer_application_status_created` (`status`, `created_at`),
  KEY `idx_volunteer_application_phone_status` (`phone`, `status`),
  UNIQUE KEY `uk_volunteer_active_phone` (`active_phone`),
  CONSTRAINT `chk_volunteer_application_status` CHECK (`status` IN ('PENDING','CONTACTED','ACCEPTED','REJECTED'))
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
