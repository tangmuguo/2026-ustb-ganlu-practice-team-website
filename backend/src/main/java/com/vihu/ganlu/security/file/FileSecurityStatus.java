package com.vihu.ganlu.security.file;

/**
 * Publication state of a file after the security pipeline has considered it.
 *
 * <p>A scanner outage is deliberately represented as {@link #PENDING}, not as
 * a successful result.  The diagnostic result is retained separately by
 * {@link FileScanResult} so operators can distinguish an outage from an
 * infected file without ever opening the file to the public.</p>
 */
public enum FileSecurityStatus {
    PENDING,
    CLEAN,
    INFECTED;

    public boolean isPublishable() {
        return this == CLEAN;
    }
}
