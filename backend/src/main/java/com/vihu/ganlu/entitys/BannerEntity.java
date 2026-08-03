package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.sql.Timestamp;
@Data
public class BannerEntity {
    Integer id;
    String title;
    String imageUrl;
    String linkUrl;
    Integer sortOrder;
    Integer isVisible;
    Timestamp createdAt;
    Timestamp updatedAt;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    String imageUploadToken;
    @JsonIgnore
    Integer imageUploadUserId;
}
