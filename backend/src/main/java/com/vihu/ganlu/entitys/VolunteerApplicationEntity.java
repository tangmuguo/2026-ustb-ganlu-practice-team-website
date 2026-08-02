package com.vihu.ganlu.entitys;

import java.util.Date;

public class VolunteerApplicationEntity {
    private Long id;
    private String name;
    private String phone;
    private String organization;
    private String gradeOrMajor;
    private String preferredRegion;
    private String skills;
    private String introduction;
    private Boolean privacyAgreed;
    private String status;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Boolean getPrivacyAgreed() { return privacyAgreed; }
    public void setPrivacyAgreed(Boolean privacyAgreed) { this.privacyAgreed = privacyAgreed; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
