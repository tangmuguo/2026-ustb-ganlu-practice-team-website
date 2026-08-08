package com.vihu.ganlu.configs;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first administrator without ever storing or printing a plaintext password.
 * The runner is a no-op unless both bootstrap environment variables are present and the
 * database does not already contain an administrator.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserService userService;
    private final String username;
    private final String password;
    private final String phone;

    public AdminBootstrapRunner(
            UserService userService,
            @Value("${ganlu.bootstrap-admin.username:}") String username,
            @Value("${ganlu.bootstrap-admin.password:}") String password,
            @Value("${ganlu.bootstrap-admin.phone:}") String phone) {
        this.userService = userService;
        this.username = username;
        this.password = password;
        this.phone = phone;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasUsername = StringUtils.hasText(username);
        boolean hasPassword = StringUtils.hasText(password);
        if (!hasUsername && !hasPassword) return;

        if (!hasUsername || !hasPassword) {
            throw new IllegalStateException("首次管理员初始化必须同时设置账号和密码");
        }
        if (!userService.findUserByLevel(0).isEmpty()) {
            log.info("数据库中已有管理员，跳过首次管理员初始化");
            return;
        }
        if (username.trim().length() < 3 || username.trim().length() > 30) {
            throw new IllegalStateException("首次管理员账号长度必须为3到30个字符");
        }
        if (password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException("首次管理员密码长度必须为8到72个字符");
        }
        if (StringUtils.hasText(phone) && !phone.trim().matches("^1[3-9]\\d{9}$")) {
            throw new IllegalStateException("首次管理员手机号必须为正确的11位手机号");
        }

        UserEntity administrator = new UserEntity();
        administrator.setUsername(username.trim());
        administrator.setPassword(password);
        administrator.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
        administrator.setLevel(0);
        userService.addUser(administrator);
        log.info("已创建首次管理员账号 {}；请立即清除初始化环境变量", administrator.getUsername());
    }
}
