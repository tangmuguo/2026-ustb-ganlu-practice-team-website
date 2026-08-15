package com.vihu.ganlu.security.file;

import java.nio.file.Path;

/** A file that is not yet allowed to leave its quarantine directory. */
public final class QuarantinedFile {
    private final String scope;
    private final int ownerUserId;
    private final String extension;
    private final String originalFilename;
    private String quarantinePath;
    private String controlledPath;
    private FileScanResult scanResult;

    public QuarantinedFile(String scope, int ownerUserId, String extension,
                           String originalFilename, String quarantinePath) {
        this.scope = scope;
        this.ownerUserId = ownerUserId;
        this.extension = extension;
        this.originalFilename = originalFilename;
        this.quarantinePath = quarantinePath;
    }

    public String getScope() { return scope; }
    public int getOwnerUserId() { return ownerUserId; }
    public String getExtension() { return extension; }
    public String getOriginalFilename() { return originalFilename; }
    public String getQuarantinePath() { return quarantinePath; }
    public String getControlledPath() { return controlledPath; }
    public FileScanResult getScanResult() { return scanResult; }
    void setControlledPath(String controlledPath) { this.controlledPath = controlledPath; }
    void setScanResult(FileScanResult scanResult) { this.scanResult = scanResult; }

    public Path path(FileStorageAccess access) {
        return access.load(quarantinePath);
    }

    /** Narrow storage abstraction used to keep this value object testable. */
    public interface FileStorageAccess {
        Path load(String relativePath);
    }
}
