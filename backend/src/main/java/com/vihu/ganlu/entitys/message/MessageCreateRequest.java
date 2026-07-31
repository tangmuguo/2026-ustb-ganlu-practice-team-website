package com.vihu.ganlu.entitys.message;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class MessageCreateRequest {
    @NotBlank(message = "留言内容不能为空")
    @Size(min = 1, max = 500, message = "留言内容长度限制1~500字")
    private String content;
}