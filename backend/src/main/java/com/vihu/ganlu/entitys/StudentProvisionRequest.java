package com.vihu.ganlu.entitys;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Input accepted only from an administrator or an authenticated team owner.
 * Verification and consent fields deliberately do not appear here: clients
 * must never be able to mark a child account as verified by themselves.
 */
public class StudentProvisionRequest {
    @NotBlank(message = "请输入登录账号")
    @Size(min = 3, max = 30, message = "账号长度应为3到30个字符")
    private String username;

    @NotBlank(message = "请输入真实姓名")
    @Size(min = 2, max = 30, message = "姓名长度应为2到30个字符")
    private String realname;

    @NotBlank(message = "请输入所属小学")
    @Size(max = 100, message = "所属小学不能超过100个字符")
    private String belongschool;

    @NotBlank(message = "请输入年级")
    @Size(max = 30, message = "年级不能超过30个字符")
    private String grade;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;

    @Size(max = 64, message = "展示名称不能超过64个字符")
    private String displayName;

    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 72, message = "密码长度应为8到72个字符")
    private String password;

    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    /** Only an administrator may supply this; service code ignores it for a team actor. */
    private Integer teamId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealname() { return realname; }
    public void setRealname(String realname) { this.realname = realname; }
    public String getBelongschool() { return belongschool; }
    public void setBelongschool(String belongschool) { this.belongschool = belongschool; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public Integer getTeamId() { return teamId; }
    public void setTeamId(Integer teamId) { this.teamId = teamId; }
}
