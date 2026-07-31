package com.vihu.ganlu.entitys.message;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class ReplyCreateRequest {
    @NotNull(message = "目标留言id不能为空")
    private Integer messageId;

    @NotBlank(message = "回复内容不能为空")
    @Size(min = 1, max = 300, message = "回复内容长度限制1~300字")
    private String content;
}