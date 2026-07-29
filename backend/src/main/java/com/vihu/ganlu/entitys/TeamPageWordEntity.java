package com.vihu.ganlu.entitys;

import lombok.Data;

import java.sql.Timestamp;
@Data
public class TeamPageWordEntity {
    Integer id;
    Integer userId;
    Integer pageId;
    String videoUrl;
    String thumbnailUrl;
    String caption;
    String content;
    Integer duration;
    Integer displayOrder;
    Timestamp createdAt;
    Timestamp updatedAt;
    Integer type;
}
