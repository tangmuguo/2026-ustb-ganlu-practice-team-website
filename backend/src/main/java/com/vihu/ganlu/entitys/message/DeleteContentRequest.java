package com.vihu.ganlu.entitys.message;

import javax.validation.constraints.NotNull;

public class DeleteContentRequest {

    @NotNull(message = "资源ID不能为空")
    private Integer id;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}