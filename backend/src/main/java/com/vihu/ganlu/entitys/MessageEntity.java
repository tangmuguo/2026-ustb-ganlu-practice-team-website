package com.vihu.ganlu.entitys;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageEntity {
    private Integer id;
    private Integer userId;
    private String content;
    private Date createTime;
    private Date updateTime;
    private Integer status;

    // 非数据库字段
    private String username;
    private String teamname;
    private List<ReplyEntity> replies;
}
