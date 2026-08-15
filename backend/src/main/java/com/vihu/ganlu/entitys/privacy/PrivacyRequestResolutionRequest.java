package com.vihu.ganlu.entitys.privacy;

import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Administrator decision input.  A reason is mandatory for every transition. */
public class PrivacyRequestResolutionRequest {
    @NotBlank
    @Pattern(regexp = "PROCESSING|APPROVED|RESOLVED|REJECTED")
    private String status;

    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}")
    @JsonAlias({"resolutionCode", "code"})
    private String decisionCode;

    @NotBlank
    @Size(max = 1000)
    @JsonAlias({"reason", "resolutionNote", "resolution_note"})
    private String decisionReason;

    @Size(max = 32)
    @JsonAlias({"preservationDecision", "retention_decision"})
    private String retentionDecision;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDecisionCode() { return decisionCode; }
    public void setDecisionCode(String decisionCode) { this.decisionCode = decisionCode; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public String getRetentionDecision() { return retentionDecision; }
    public void setRetentionDecision(String retentionDecision) { this.retentionDecision = retentionDecision; }
}
