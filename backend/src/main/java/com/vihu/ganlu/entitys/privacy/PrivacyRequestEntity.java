package com.vihu.ganlu.entitys.privacy;

import java.util.Date;

/**
 * Internal privacy-rights ticket.  This object is never returned directly from
 * a controller; use {@link PrivacyRequestViewDto} so requester/user fields and
 * handling metadata stay on the minimum-necessary side of the API boundary.
 */
public class PrivacyRequestEntity {
    private Long id;
    private Integer requesterUserId;
    private String requestType;
    private String consentType;
    private String scopeCode;
    private String description;
    private String status;
    private Integer handledByUserId;
    private Date handledAt;
    private String decisionCode;
    private String decisionReason;
    private String retentionDecision;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getRequesterUserId() { return requesterUserId; }
    public void setRequesterUserId(Integer requesterUserId) { this.requesterUserId = requesterUserId; }
    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }
    public String getScopeCode() { return scopeCode; }
    public void setScopeCode(String scopeCode) { this.scopeCode = scopeCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getHandledByUserId() { return handledByUserId; }
    public void setHandledByUserId(Integer handledByUserId) { this.handledByUserId = handledByUserId; }
    public Date getHandledAt() { return handledAt; }
    public void setHandledAt(Date handledAt) { this.handledAt = handledAt; }
    public String getDecisionCode() { return decisionCode; }
    public void setDecisionCode(String decisionCode) { this.decisionCode = decisionCode; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public String getRetentionDecision() { return retentionDecision; }
    public void setRetentionDecision(String retentionDecision) { this.retentionDecision = retentionDecision; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
