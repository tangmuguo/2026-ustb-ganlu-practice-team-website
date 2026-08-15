package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplyEntity {
    private Integer id;
    private Integer messageId;
    private Integer userId;
    private String content;
    private Date createTime;
    private Date updateTime;
    private Boolean status;
    @JsonIgnore private String contentStatus;
    @JsonIgnore private Integer reviewedByUserId;
    @JsonIgnore private Date reviewedAt;
    @JsonIgnore private String reviewReasonCode;
    @JsonIgnore private String reviewNote;
    @JsonIgnore private Integer removedByUserId;
    @JsonIgnore private Date removedAt;
    @JsonIgnore private String removalReasonCode;

    // 非数据库字段
    @JsonIgnore private String username;
    @JsonIgnore private String teamname;
    @JsonIgnore private Integer userLevel;
    private String displayName;
}
