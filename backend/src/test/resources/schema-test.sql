-- 用户表
CREATE TABLE IF NOT EXISTS user (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    level INT NOT NULL DEFAULT 2,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
-- 插入前允许手动指定自增ID
SET IDENTITY_INSERT user ON;

-- 留言表
CREATE TABLE IF NOT EXISTS message (
    id INT PRIMARY KEY AUTO_INCREMENT,
    content VARCHAR(500) NOT NULL,
    user_id INT NOT NULL,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 回复表
CREATE TABLE IF NOT EXISTS message_reply (
    id INT PRIMARY KEY AUTO_INCREMENT,
    message_id INT NOT NULL,
    content VARCHAR(300) NOT NULL,
    user_id INT NOT NULL,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 把项目里其他所有表（banner、course、file等）都补在这里，和生产库结构一致