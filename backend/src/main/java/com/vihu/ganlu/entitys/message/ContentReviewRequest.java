package com.vihu.ganlu.entitys.message;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** Administrator-only moderation decision; original content is never overwritten. */
public class ContentReviewRequest {
    @NotBlank
    @Pattern(regexp = "MESSAGE|REPLY")
    private String contentType;

    @NotNull
    private Integer contentId;

    @NotBlank
    @Pattern(regexp = "APPROVED|REJECTED|REMOVED")
    private String decision;

    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}")
    private String reasonCode;

    @Size(max = 500)
    private String note;

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Integer getContentId() { return contentId; }
    public void setContentId(Integer contentId) { this.contentId = contentId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
