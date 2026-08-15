package com.vihu.ganlu.entitys.privacy;

import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Client input for a privacy-rights ticket.  The requester identity is taken from the token. */
public class PrivacyRequestCreateRequest {
    @NotBlank
    @Pattern(regexp = "CORRECTION|DELETION|WITHDRAW_CONSENT")
    @JsonAlias({"type", "request_type"})
    private String requestType;

    @Pattern(regexp = "GUARDIAN|PRIVACY")
    @JsonAlias({"consent", "consent_type"})
    private String consentType;

    @Size(max = 64)
    @JsonAlias({"scope_code", "field"})
    private String scope;

    @NotBlank
    @Size(max = 2000)
    @JsonAlias({"reason", "requestDescription", "request_description"})
    private String description;

    public String getRequestType() { return requestType; }
    public void setRequestType(String requestType) { this.requestType = requestType; }
    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
