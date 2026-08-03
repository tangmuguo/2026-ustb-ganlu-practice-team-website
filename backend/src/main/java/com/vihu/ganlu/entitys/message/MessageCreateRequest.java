package com.vihu.ganlu.entitys.message;

import javax.validation.constraints.NotBlank;

public class MessageCreateRequest {

    @NotBlank(message = "留言内容不可为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}