package com.vihu.ganlu.entitys.message;

import lombok.Data;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.NotNull;

@Data
public class DeleteContentRequest {
    @NotNull
    private Integer id;

    /** Required only when an administrator removes somebody else's content. */
    @Pattern(regexp = "^$|^[A-Z][A-Z0-9_]{1,63}$", message = "处置原因代码格式不正确")
    private String reasonCode;
}
