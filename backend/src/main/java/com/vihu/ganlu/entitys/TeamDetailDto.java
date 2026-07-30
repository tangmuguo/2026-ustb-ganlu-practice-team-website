package com.vihu.ganlu.entitys;

import java.sql.Timestamp;

public class TeamDetailDto {
    private Integer id;
    private String year;
    private String name;
    private String region;
    private String school;
    private String description;
    private String coverUrl;
    private TeamEntity.Status status;
    private Integer pageId;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public static TeamDetailDto from(TeamEntity team, Integer pageId) {
        TeamDetailDto detail = new TeamDetailDto();
        detail.setId(team.getId());
        detail.setYear(team.getYear());
        detail.setName(team.getName());
        detail.setRegion(team.getRegion());
        detail.setSchool(team.getSchool());
        detail.setDescription(team.getDescription());
        detail.setCoverUrl(team.getCoverUrl());
        detail.setStatus(team.getStatus());
        detail.setPageId(pageId);
        detail.setCreatedAt(team.getCreatedAt());
        detail.setUpdatedAt(team.getUpdatedAt());
        return detail;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSchool() {
        return school;
    }

    public void setSchool(String school) {
        this.school = school;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public TeamEntity.Status getStatus() {
        return status;
    }

    public void setStatus(TeamEntity.Status status) {
        this.status = status;
    }

    public Integer getPageId() {
        return pageId;
    }

    public void setPageId(Integer pageId) {
        this.pageId = pageId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}
