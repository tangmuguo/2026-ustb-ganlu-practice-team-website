package com.vihu.ganlu.entitys;

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
}
