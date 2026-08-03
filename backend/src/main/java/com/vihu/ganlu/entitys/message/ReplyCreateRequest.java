package com.vihu.ganlu.entitys.message;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ReplyCreateRequest {

    @NotNull(message = "留言ID不能为空")
    private Integer messageId;

    @NotBlank(message = "回复内容不可为空")
    private String content;

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}