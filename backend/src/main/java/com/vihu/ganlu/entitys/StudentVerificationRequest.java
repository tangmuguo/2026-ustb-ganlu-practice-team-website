package com.vihu.ganlu.entitys;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * Administrator-only record of a completed offline check. It deliberately
 * accepts no identity document number or document image.
 */
public class StudentVerificationRequest {
    @NotBlank
    @Pattern(regexp = "VERIFIED|REJECTED|SUSPENDED")
    private String verificationStatus;

    @Size(max = 32)
    private String verificationMethod;

    @NotBlank
    @Pattern(regexp = "PENDING|CONSENTED|WITHDRAWN")
    private String guardianConsentStatus;

    @Size(max = 32)
    private String guardianConsentVersion;

    @Size(max = 32)
    private String privacyConsentVersion;

    @Pattern(regexp = "^$|^[A-Fa-f0-9]{64}$")
    private String evidenceDigest;

    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public String getVerificationMethod() { return verificationMethod; }
    public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }
    public String getGuardianConsentStatus() { return guardianConsentStatus; }
    public void setGuardianConsentStatus(String guardianConsentStatus) { this.guardianConsentStatus = guardianConsentStatus; }
    public String getGuardianConsentVersion() { return guardianConsentVersion; }
    public void setGuardianConsentVersion(String guardianConsentVersion) { this.guardianConsentVersion = guardianConsentVersion; }
    public String getPrivacyConsentVersion() { return privacyConsentVersion; }
    public void setPrivacyConsentVersion(String privacyConsentVersion) { this.privacyConsentVersion = privacyConsentVersion; }
    public String getEvidenceDigest() { return evidenceDigest; }
    public void setEvidenceDigest(String evidenceDigest) { this.evidenceDigest = evidenceDigest; }
}
