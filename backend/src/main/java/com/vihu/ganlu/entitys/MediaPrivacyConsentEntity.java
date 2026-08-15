package com.vihu.ganlu.entitys;

import java.util.Date;

/** Minimal, auditable consent record; evidence is a digest only. */
public class MediaPrivacyConsentEntity {
    private Long id;
    private String assetType;
    private Long assetId;
    private Integer subjectUserId;
    private String consentStatus;
    private String policyVersion;
    private String evidenceDigest;
    private Date grantedAt;
    private Date withdrawnAt;
    private Integer recordedByUserId;
    private Long auditEventId;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public Integer getSubjectUserId() { return subjectUserId; }
    public void setSubjectUserId(Integer subjectUserId) { this.subjectUserId = subjectUserId; }
    public String getConsentStatus() { return consentStatus; }
    public void setConsentStatus(String consentStatus) { this.consentStatus = consentStatus; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getEvidenceDigest() { return evidenceDigest; }
    public void setEvidenceDigest(String evidenceDigest) { this.evidenceDigest = evidenceDigest; }
    public Date getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Date grantedAt) { this.grantedAt = grantedAt; }
    public Date getWithdrawnAt() { return withdrawnAt; }
    public void setWithdrawnAt(Date withdrawnAt) { this.withdrawnAt = withdrawnAt; }
    public Integer getRecordedByUserId() { return recordedByUserId; }
    public void setRecordedByUserId(Integer recordedByUserId) { this.recordedByUserId = recordedByUserId; }
    public Long getAuditEventId() { return auditEventId; }
    public void setAuditEventId(Long auditEventId) { this.auditEventId = auditEventId; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
