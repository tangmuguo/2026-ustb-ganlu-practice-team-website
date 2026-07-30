package com.vihu.ganlu.entitys;

import java.sql.Timestamp;

public class TeamPageEntity {
    public enum Status {
        DRAFT("草稿"),
        PUBLISHED("展示"),
        ARCHIVED("归档");

        private final String databaseValue;

        Status(String databaseValue) {
            this.databaseValue = databaseValue;
        }

        public String getDatabaseValue() {
            return databaseValue;
        }
    }

    private Integer id;
    private String title;
    private String content;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Integer teamId;
    private Status status = Status.DRAFT;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Integer getTeamId() {
        return teamId;
    }

    public void setTeamId(Integer teamId) {
        this.teamId = teamId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
