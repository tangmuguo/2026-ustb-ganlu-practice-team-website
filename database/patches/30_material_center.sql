-- 甘露网站第二阶段：课件中心增量补丁
-- 依赖：先导入根目录 ganlu.sql，再执行本文件。
-- 特性：不 DROP 表，不删除历史课件；重复执行时会跳过已存在的列和索引。

SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE course ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''1启用 0停用'' AFTER course_name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'uploader_user_id') = 0,
    'ALTER TABLE course_detail ADD COLUMN uploader_user_id INT NULL COMMENT ''上传者用户ID'' AFTER author', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'year') = 0,
    'ALTER TABLE course_detail ADD COLUMN year SMALLINT NULL COMMENT ''课件年份'' AFTER course_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'custom_subject') = 0,
    'ALTER TABLE course_detail ADD COLUMN custom_subject VARCHAR(30) NULL COMMENT ''特色课程自定义科目'' AFTER year', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'cover_path') = 0,
    'ALTER TABLE course_detail ADD COLUMN cover_path VARCHAR(500) NULL COMMENT ''封面相对路径'' AFTER thumbnail_url', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'original_file_path') = 0,
    'ALTER TABLE course_detail ADD COLUMN original_file_path VARCHAR(500) NULL COMMENT ''受保护原文件相对路径'' AFTER files', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'preview_file_path') = 0,
    'ALTER TABLE course_detail ADD COLUMN preview_file_path VARCHAR(500) NULL COMMENT ''受保护预览文件相对路径'' AFTER original_file_path', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'original_filename') = 0,
    'ALTER TABLE course_detail ADD COLUMN original_filename VARCHAR(255) NULL COMMENT ''下载时使用的原文件名'' AFTER preview_file_path', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'file_extension') = 0,
    'ALTER TABLE course_detail ADD COLUMN file_extension VARCHAR(10) NULL COMMENT ''经后端校验的扩展名'' AFTER file_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'mime_type') = 0,
    'ALTER TABLE course_detail ADD COLUMN mime_type VARCHAR(100) NULL COMMENT ''经后端校验的MIME'' AFTER file_extension', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'preview_status') = 0,
    'ALTER TABLE course_detail ADD COLUMN preview_status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' COMMENT ''PENDING/READY/FAILED'' AFTER mime_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND COLUMN_NAME = 'status') = 0,
    'ALTER TABLE course_detail ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''1有效 0已删除'' AFTER preview_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史记录只做字段回填，不移动或删除磁盘文件。公共图片迁移会读取这些有效封面引用，
-- 但课件封面仍由课件模块独立管理，不进入 public_image_asset 配额账本。
UPDATE course_detail
SET year = YEAR(COALESCE(create_time, CURRENT_TIMESTAMP))
WHERE year IS NULL;

UPDATE course_detail
SET cover_path = thumbnail_url
WHERE cover_path IS NULL AND thumbnail_url IS NOT NULL;

UPDATE course_detail
SET original_file_path = files,
    original_filename = SUBSTRING_INDEX(REPLACE(files, '\\', '/'), '/', -1),
    file_extension = LOWER(SUBSTRING_INDEX(files, '.', -1)),
    mime_type = COALESCE(mime_type, file_type),
    preview_status = 'FAILED'
WHERE original_file_path IS NULL AND files IS NOT NULL;

INSERT INTO course(course_name, status)
SELECT seed.course_name, 1
FROM (
    SELECT '语文' AS course_name UNION ALL
    SELECT '数学' UNION ALL
    SELECT '英语'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM course existing WHERE existing.course_name = seed.course_name
);

-- 保留历史引用，不物理删除重复科目；将重复项停用并重命名后建立数据库唯一约束。
UPDATE course duplicate_course
JOIN (
    SELECT course_name, MIN(id) AS keep_id
    FROM course
    WHERE course_name IS NOT NULL
    GROUP BY course_name
    HAVING COUNT(*) > 1
) duplicate_names
    ON duplicate_course.course_name = duplicate_names.course_name
   AND duplicate_course.id <> duplicate_names.keep_id
SET duplicate_course.course_name = CONCAT(
        LEFT(duplicate_course.course_name, 220), '-重复-', duplicate_course.id
    ),
    duplicate_course.status = 0;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course' AND INDEX_NAME = 'uk_course_name') = 0,
    'CREATE UNIQUE INDEX uk_course_name ON course(course_name)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND INDEX_NAME = 'idx_material_public_filter') = 0,
    'CREATE INDEX idx_material_public_filter ON course_detail(status, year, courseType, course_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'course_detail' AND INDEX_NAME = 'idx_material_uploader') = 0,
    'CREATE INDEX idx_material_uploader ON course_detail(uploader_user_id, status)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验查询：启用的通识科目应为语文、数学、英语；课件仍可保留历史科目关联。
SELECT COUNT(*) AS active_course_count FROM course WHERE status = 1;
SELECT courseType, COUNT(*) AS material_count FROM course_detail WHERE status = 1 GROUP BY courseType;
