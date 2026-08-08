package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Date;

@Data
public class NewsEntity {
    int id;
    String caption;
    String content;
    String imageUrl;
    Date createAt;
    String linkUrl;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String imageUploadToken;
    @JsonIgnore
    Integer imageUploadUserId;
}
