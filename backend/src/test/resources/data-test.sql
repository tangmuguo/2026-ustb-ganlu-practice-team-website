-- 管理员 level=0：拥有全部权限
INSERT INTO user (id, username, level) VALUES (1000, 'admin_root', 0);

-- 团队成员 level=1：可留言、回复、删除内容
INSERT INTO user (id, username, level) VALUES (1001, 'team_member', 1);

-- 学生用户1 level=2：仅可留言、回复，无删除权限
INSERT INTO user (id, username, level) VALUES (1002, 'student_user1', 2);

-- 学生用户2 level=2：用于多用户场景测试
INSERT INTO user (id, username, level) VALUES (2001, 'student_user2', 2);