package com.vihu.ganlu.security.file;

/** Fail-closed exception used when a file is not safe to publish or download. */
public class FileSecurityException extends IllegalStateException {
    private final FileScanResult scanResult;

    public FileSecurityException(String message) {
        super(message);
        this.scanResult = null;
    }

    public FileSecurityException(String message, FileScanResult scanResult) {
        super(message);
        this.scanResult = scanResult;
    }

    public FileScanResult getScanResult() { return scanResult; }
}
