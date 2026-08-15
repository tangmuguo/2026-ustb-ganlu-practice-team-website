package com.vihu.ganlu.entitys;

import java.util.Date;

public class StudentTeamAssignmentEntity {
    private Long id;
    private Integer studentUserId;
    private Integer teamId;
    private Integer assignedByUserId;
    private Date assignedAt;
    private Date revokedAt;
    private String scope = "MANAGE";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getStudentUserId() { return studentUserId; }
    public void setStudentUserId(Integer studentUserId) { this.studentUserId = studentUserId; }
    public Integer getTeamId() { return teamId; }
    public void setTeamId(Integer teamId) { this.teamId = teamId; }
    public Integer getAssignedByUserId() { return assignedByUserId; }
    public void setAssignedByUserId(Integer assignedByUserId) { this.assignedByUserId = assignedByUserId; }
    public Date getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Date assignedAt) { this.assignedAt = assignedAt; }
    public Date getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Date revokedAt) { this.revokedAt = revokedAt; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
}
