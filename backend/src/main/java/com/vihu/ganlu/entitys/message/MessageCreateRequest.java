package com.vihu.ganlu.entitys.message;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class MessageCreateRequest {

    @NotBlank(message = "留言内容不可为空")
    @Size(min = 1, max = 500, message = "留言内容长度限制1~500字符")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}