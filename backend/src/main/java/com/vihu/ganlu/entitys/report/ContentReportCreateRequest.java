package com.vihu.ganlu.entitys.report;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ContentReportCreateRequest {
    @NotBlank
    @Pattern(regexp = "MESSAGE|REPLY|TEAM_IMAGE|TEAM_WORD|TEAM_MEDIA")
    private String targetType;

    @NotNull
    @Min(1)
    private Integer targetId;

    @NotBlank
    @Pattern(regexp = "HARASSMENT|HARMFUL|PRIVACY|FRAUD|COPYRIGHT|OTHER")
    private String category;

    @Size(max = 1000)
    private String description;

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Integer getTargetId() { return targetId; }
    public void setTargetId(Integer targetId) { this.targetId = targetId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
