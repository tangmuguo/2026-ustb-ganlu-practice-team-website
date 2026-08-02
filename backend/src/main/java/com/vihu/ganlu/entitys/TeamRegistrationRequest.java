package com.vihu.ganlu.entitys;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class TeamRegistrationRequest {
    @NotBlank(message = "请输入登录账号")
    @Size(min = 3, max = 30, message = "账号长度应为3到30个字符")
    private String username;
    @NotBlank(message = "请输入团队简称")
    @Size(min = 2, max = 100, message = "团队简称长度应为2到100个字符")
    private String teamname;
    @NotBlank(message = "请输入支教地")
    @Size(max = 100, message = "支教地不能超过100个字符")
    private String helplocation;
    @NotBlank(message = "请输入支教小学")
    @Size(max = 150, message = "支教小学不能超过150个字符")
    private String helpschool;
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;
    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 72, message = "密码长度应为8到72个字符")
    private String password;
    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getTeamname() { return teamname; }
    public void setTeamname(String teamname) { this.teamname = teamname; }
    public String getHelplocation() { return helplocation; }
    public void setHelplocation(String helplocation) { this.helplocation = helplocation; }
    public String getHelpschool() { return helpschool; }
    public void setHelpschool(String helpschool) { this.helpschool = helpschool; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
