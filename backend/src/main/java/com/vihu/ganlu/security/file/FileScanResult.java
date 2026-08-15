package com.vihu.ganlu.security.file;

import java.util.Date;

/** Immutable, non-sensitive result returned by the file security pipeline. */
public final class FileScanResult {
    private final String relativePath;
    private final FileSecurityStatus status;
    private final MalwareScanner.ScanVerdict diagnosticVerdict;
    private final String detail;
    private final String sha256;
    private final Date completedAt;

    public FileScanResult(String relativePath,
                          FileSecurityStatus status,
                          MalwareScanner.ScanVerdict diagnosticVerdict,
                          String detail,
                          String sha256,
                          Date completedAt) {
        this.relativePath = relativePath;
        this.status = status == null ? FileSecurityStatus.PENDING : status;
        this.diagnosticVerdict = diagnosticVerdict == null
                ? MalwareScanner.ScanVerdict.FAILED : diagnosticVerdict;
        this.detail = limit(detail, 500);
        this.sha256 = sha256;
        this.completedAt = completedAt == null ? new Date() : new Date(completedAt.getTime());
    }

    public String getRelativePath() { return relativePath; }
    public FileSecurityStatus getStatus() { return status; }
    public MalwareScanner.ScanVerdict getDiagnosticVerdict() { return diagnosticVerdict; }
    public String getDetail() { return detail; }
    public String getSha256() { return sha256; }
    public Date getCompletedAt() { return new Date(completedAt.getTime()); }
    public boolean isClean() { return status.isPublishable(); }

    private static String limit(String value, int max) {
        if (value == null) return null;
        String normalized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
