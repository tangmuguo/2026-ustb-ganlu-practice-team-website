MERGE INTO user (id, username, password, teamname, level, status)
KEY(id)
VALUES 
    (1001, 'test_admin', 'test123456', 'practice-team', 0, 1),
    (1002, 'test_team', 'test123456', 'practice-team', 1, 1),
    (2001, 'test_student', 'test123456', 'practice-team', 2, 1);