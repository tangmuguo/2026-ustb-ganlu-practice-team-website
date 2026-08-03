package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.sql.Timestamp;
@Data
public class TeamPageImageEntity {
    Integer id;
    Integer userId;
    Integer pageId;
    String imageUrl;
    String caption;
    String content;
    Integer displayOrder;
    Timestamp createdAt;
    Timestamp updatedAt;
    Integer type;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String imageUploadToken;
    @JsonIgnore
    Integer imageUploadUserId;
}
