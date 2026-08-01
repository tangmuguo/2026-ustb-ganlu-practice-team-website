-- 先清空所有旧数据（包括NULL脏数据）
DELETE FROM ganlu.user;

-- 插入3条测试账号
INSERT INTO ganlu.user (id, username, level, password) VALUES (1, 'admin', 0, '123456');
INSERT INTO ganlu.user (id, username, level, password) VALUES (1001, 'group', 1, '123456');
INSERT INTO ganlu.user (id, username, level, password) VALUES (2001, 'student', 2, '123456');

-- 查询验证
SELECT id, username, level FROM ganlu.user;