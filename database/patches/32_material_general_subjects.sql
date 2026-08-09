-- 课件通识科目收敛为语文、数学、英语。
-- 依赖：已执行 30_material_center.sql（其中会创建 course.status 和 uk_course_name）。
-- 不删除旧科目或历史课件；旧科目仅停用，以保留已有课件的关联与展示。

SET NAMES utf8mb4;

INSERT INTO course(course_name, status)
VALUES
    ('语文', 1),
    ('数学', 1),
    ('英语', 1)
ON DUPLICATE KEY UPDATE status = 1;

UPDATE course
SET status = 0
WHERE course_name NOT IN ('语文', '数学', '英语')
  AND status <> 0;

-- 校验：应只返回语文、数学、英语三项。
SELECT id, course_name, status
FROM course
WHERE status = 1
ORDER BY id ASC;
