package com.vihu.ganlu.entitys;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class LoginRequest {
    @NotBlank(message = "请输入账号")
    @Size(max = 30, message = "账号不能超过30个字符")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 72, message = "密码长度应为6到72个字符")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
