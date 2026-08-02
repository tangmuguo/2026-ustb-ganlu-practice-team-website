-- 确保字段名和实体类、表结构完全对应，status=1保证正常状态可被查询到
INSERT INTO user (id, username, password, level, status) VALUES (1001, 'test_admin', '123456', 0, 1);
INSERT INTO user (id, username, password, level, status) VALUES (1002, 'test_team', '123456', 1, 1);
INSERT INTO user (id, username, password, level, status) VALUES (2001, 'test_student', '123456', 2, 1);