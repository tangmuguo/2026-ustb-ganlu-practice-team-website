-- 仅用于单元测试的测试账号，不会执行到共享主库
INSERT INTO user (id, username, password, level) VALUES
(1, 'admin', '加密后的密码', 0),
(2, 'team_user', '加密后的密码', 1),
(3, 'student_user', '加密后的密码', 2);