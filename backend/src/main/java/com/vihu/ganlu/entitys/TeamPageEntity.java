package com.vihu.ganlu.entitys;

import java.sql.Timestamp;

public class TeamPageEntity {
    public enum Status {
        DRAFT("草稿"),
        DISPLAY("展示"),
        ARCHIVE("归档");

        private final String description;

        // 构造函数
        Status(String description) {
            this.description = description;
        }

        // 获取描述信息
        public String getDescription() {
            return description;
        }
    }

    Integer id;
    String title;
    String content;
    Timestamp created_at;
    Timestamp updated_at;
    Integer team_id;
    Status status=Status.DRAFT;

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

    public Timestamp getCreated_at() {
        return created_at;
    }

    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }

    public Timestamp getUpdated_at() {
        return updated_at;
    }

    public void setUpdated_at(Timestamp updated_at) {
        this.updated_at = updated_at;
    }

    public Integer getTeam_id() {
        return team_id;
    }

    public void setTeam_id(Integer team_id) {
        this.team_id = team_id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
