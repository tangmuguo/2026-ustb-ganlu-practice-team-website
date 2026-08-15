package com.vihu.ganlu.entitys;

import java.util.Date;

public class AuditEventEntity {
    private Long id;
    private String requestId;
    private Date occurredAt;
    private Integer actorUserId;
    private Integer actorRole;
    private String action;
    private String resourceType;
    private String resourceId;
    private String outcome;
    private String httpMethod;
    private String requestPath;
    private String sourceIp;
    private String targetHost;
    private Integer targetPort;
    private String userAgent;
    private String reasonCode;
    private String metadataJson;
    private Date retentionUntil;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Date getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Date occurredAt) { this.occurredAt = occurredAt; }
    public Integer getActorUserId() { return actorUserId; }
    public void setActorUserId(Integer actorUserId) { this.actorUserId = actorUserId; }
    public Integer getActorRole() { return actorRole; }
    public void setActorRole(Integer actorRole) { this.actorRole = actorRole; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }
    public String getSourceIp() { return sourceIp; }
    public void setSourceIp(String sourceIp) { this.sourceIp = sourceIp; }
    public String getTargetHost() { return targetHost; }
    public void setTargetHost(String targetHost) { this.targetHost = targetHost; }
    public Integer getTargetPort() { return targetPort; }
    public void setTargetPort(Integer targetPort) { this.targetPort = targetPort; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public Date getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(Date retentionUntil) { this.retentionUntil = retentionUntil; }
}
