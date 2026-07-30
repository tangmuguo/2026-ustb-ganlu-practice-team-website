package com.vihu.ganlu.entitys;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;
@Data
public class TeamPageImageEntity {
    Integer id;
    Integer teamId;
    String status;
    String rejectReason;
    Date logDate;
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
