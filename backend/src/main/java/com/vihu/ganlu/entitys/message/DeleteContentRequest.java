package com.vihu.ganlu.entitys.message;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class DeleteContentRequest {
    @NotNull(message = "待删除资源id不能为空")
    private Integer id;
}