package com.vihu.ganlu.entitys.report;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ContentReportResolutionRequest {
    @NotBlank
    @Pattern(regexp = "PROCESSING|RESOLVED|REJECTED")
    private String status;

    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}")
    private String resolutionCode;

    @Size(max = 1000)
    private String resolutionNote;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolutionCode() { return resolutionCode; }
    public void setResolutionCode(String resolutionCode) { this.resolutionCode = resolutionCode; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
}
