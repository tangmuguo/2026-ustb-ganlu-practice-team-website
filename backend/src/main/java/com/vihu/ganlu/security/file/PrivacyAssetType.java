package com.vihu.ganlu.security.file;

/** Business asset classes whose publication may expose a child. */
public enum PrivacyAssetType {
    CHILD_PHOTO,
    CHILD_VIDEO,
    CLASSROOM_LOG;

    public boolean requiresGuardianConsent() {
        return true;
    }
}
