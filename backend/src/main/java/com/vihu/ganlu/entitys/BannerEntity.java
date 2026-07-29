package com.vihu.ganlu.entitys;

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
}
