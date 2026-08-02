-- 初始化测试用户，严格对齐业务权限规则
-- level=0 管理员（拥有全部权限）
INSERT INTO user (id, username, level, status) VALUES (1001, 'test_admin', 0, 1);
-- level=1 团队成员（拥有删除权限）
INSERT INTO user (id, username, level, status) VALUES (1002, 'test_team', 1, 1);
-- level=2 学生用户（无删除权限）
INSERT INTO user (id, username, level, status) VALUES (2001, 'test_student', 2, 1);