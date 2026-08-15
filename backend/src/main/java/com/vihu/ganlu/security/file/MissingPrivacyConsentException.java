package com.vihu.ganlu.security.file;

/** Default-deny publication result when no current guardian consent exists. */
public class MissingPrivacyConsentException extends FileSecurityException {
    public MissingPrivacyConsentException(String message) {
        super(message);
    }
}
