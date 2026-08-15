package com.vihu.ganlu.entitys.privacy;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

/**
 * Minimum-necessary representation for both the requester and administrator.
 * It deliberately contains no username, phone, school, consent evidence or
 * raw authentication material.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrivacyRequestViewDto {
    private Long id;
    private Integer requesterUserId;
    private String requestType;
    private String consentType;
    private String scope;
    private String description;
    private String status;
    private String decisionCode;
    private String decisionReason;
    private String retentionDecision;
    private Date createdAt;
    private Date updatedAt;
    private Date handledAt;

    public static PrivacyRequestViewDto from(PrivacyRequestEntity entity, boolean administrator) {
        if (entity == null) return null;
        PrivacyRequestViewDto result = new PrivacyRequestViewDto();
        result.id = entity.getId();
        result.requesterUserId = administrator ? entity.getRequesterUserId() : null;
        result.requestType = entity.getRequestType();
        result.consentType = entity.getConsentType();
        result.scope = entity.getScopeCode();
        result.description = entity.getDescription();
        result.status = entity.getStatus();
        result.decisionCode = entity.getDecisionCode();
        result.decisionReason = entity.getDecisionReason();
        result.retentionDecision = entity.getRetentionDecision();
        result.createdAt = entity.getCreatedAt();
        result.updatedAt = entity.getUpdatedAt();
        result.handledAt = entity.getHandledAt();
        return result;
    }

    public Long getId() { return id; }
    public Integer getRequesterUserId() { return requesterUserId; }
    public String getRequestType() { return requestType; }
    public String getConsentType() { return consentType; }
    public String getScope() { return scope; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getDecisionCode() { return decisionCode; }
    public String getDecisionReason() { return decisionReason; }
    public String getRetentionDecision() { return retentionDecision; }
    public Date getCreatedAt() { return createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public Date getHandledAt() { return handledAt; }
}
