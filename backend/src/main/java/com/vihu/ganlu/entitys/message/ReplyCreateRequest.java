package com.vihu.ganlu.entitys.message;

import lombok.Data;

@Data
public class ReplyCreateRequest {
    private Integer messageId;
    private String content;
}
