package com.vihu.ganlu.entitys;

import java.util.Date;

/** Minimal scan ledger row. It intentionally stores a digest, not file bytes. */
public class FileSecurityScanEntity {
    private Long id;
    private String storageScope;
    private String relativePath;
    private Integer ownerUserId;
    private String scanStatus;
    private String diagnosticStatus;
    private String scannerName;
    private String scannerVersion;
    private String sha256;
    private String detail;
    private Date startedAt;
    private Date completedAt;
    private Date createdAt;
    private Date updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStorageScope() { return storageScope; }
    public void setStorageScope(String storageScope) { this.storageScope = storageScope; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public Integer getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Integer ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getScanStatus() { return scanStatus; }
    public void setScanStatus(String scanStatus) { this.scanStatus = scanStatus; }
    public String getDiagnosticStatus() { return diagnosticStatus; }
    public void setDiagnosticStatus(String diagnosticStatus) { this.diagnosticStatus = diagnosticStatus; }
    public String getScannerName() { return scannerName; }
    public void setScannerName(String scannerName) { this.scannerName = scannerName; }
    public String getScannerVersion() { return scannerVersion; }
    public void setScannerVersion(String scannerVersion) { this.scannerVersion = scannerVersion; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Date getStartedAt() { return startedAt; }
    public void setStartedAt(Date startedAt) { this.startedAt = startedAt; }
    public Date getCompletedAt() { return completedAt; }
    public void setCompletedAt(Date completedAt) { this.completedAt = completedAt; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
