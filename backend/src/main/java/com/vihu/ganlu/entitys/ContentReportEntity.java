package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;

/** Internal complaint ticket. It intentionally stores no reporter contact detail. */
public class ContentReportEntity {
    public static final String TARGET_MESSAGE = "MESSAGE";
    public static final String TARGET_REPLY = "REPLY";
    public static final String TARGET_TEAM_IMAGE = "TEAM_IMAGE";
    public static final String TARGET_TEAM_WORD = "TEAM_WORD";
    public static final String TARGET_TEAM_MEDIA = "TEAM_MEDIA";

    private Long id;
    @JsonIgnore
    private Integer reporterUserId;
    private String targetType;
    private Integer targetId;
    private String category;
    private String description;
    private String status;
    @JsonIgnore
    private Integer handledByUserId;
    private Date handledAt;
    private String resolutionCode;
    private String resolutionNote;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(Integer reporterUserId) { this.reporterUserId = reporterUserId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Integer getTargetId() { return targetId; }
    public void setTargetId(Integer targetId) { this.targetId = targetId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getHandledByUserId() { return handledByUserId; }
    public void setHandledByUserId(Integer handledByUserId) { this.handledByUserId = handledByUserId; }
    public Date getHandledAt() { return handledAt; }
    public void setHandledAt(Date handledAt) { this.handledAt = handledAt; }
    public String getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(String resolutionCode) { this.resolutionCode = resolutionCode; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
