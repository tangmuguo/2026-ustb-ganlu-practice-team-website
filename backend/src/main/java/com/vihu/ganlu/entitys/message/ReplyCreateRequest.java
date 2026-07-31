package com.vihu.ganlu.entitys.message;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ReplyCreateRequest {

    @NotNull(message = "留言ID不能为空")
    private Integer messageId;

    @NotBlank(message = "回复内容不可为空")
    @Size(min = 1, max = 300, message = "回复内容长度限制1~300字符")
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