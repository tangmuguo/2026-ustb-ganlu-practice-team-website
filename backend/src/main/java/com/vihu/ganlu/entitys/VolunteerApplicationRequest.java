package com.vihu.ganlu.entitys;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class VolunteerApplicationRequest {
    @NotBlank(message = "请输入姓名")
    @Size(min = 2, max = 30, message = "姓名长度应为2到30个字符")
    private String name;
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
    private String phone;
    @NotBlank(message = "请输入学校或单位")
    @Size(max = 100, message = "学校或单位不能超过100个字符")
    private String organization;
    @Size(max = 100, message = "年级或专业不能超过100个字符")
    private String gradeOrMajor;
    @Size(max = 100, message = "意向地区不能超过100个字符")
    private String preferredRegion;
    @Size(max = 300, message = "擅长方向不能超过300个字符")
    private String skills;
    @NotBlank(message = "请填写自我介绍与报名原因")
    @Size(min = 10, max = 1000, message = "自我介绍应为10到1000个字符")
    private String introduction;
    @AssertTrue(message = "请先阅读并同意隐私说明")
    private boolean privacyAgreed;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }
    public String getGradeOrMajor() { return gradeOrMajor; }
    public void setGradeOrMajor(String gradeOrMajor) { this.gradeOrMajor = gradeOrMajor; }
    public String getPreferredRegion() { return preferredRegion; }
    public void setPreferredRegion(String preferredRegion) { this.preferredRegion = preferredRegion; }
    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }
    public String getIntroduction() { return introduction; }
    public void setIntroduction(String introduction) { this.introduction = introduction; }
    public boolean isPrivacyAgreed() { return privacyAgreed; }
    public void setPrivacyAgreed(boolean privacyAgreed) { this.privacyAgreed = privacyAgreed; }
}
