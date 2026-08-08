package com.vihu.ganlu.entitys;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class VolunteerApplicationStatusRequest {
    @NotBlank(message = "请选择报名状态")
    @Pattern(regexp = "^(PENDING|CONTACTED|ACCEPTED|REJECTED)$", message = "报名状态不正确")
    private String status;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
