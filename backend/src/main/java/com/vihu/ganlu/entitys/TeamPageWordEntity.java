package com.vihu.ganlu.entitys;

import lombok.Data;

import java.sql.Date;
import java.sql.Timestamp;
@Data
public class TeamPageWordEntity {
    Integer id;
    Integer teamId;
    String status;
    String rejectReason;
    String scanStatus;
    String scanDiagnosticStatus;
    Date logDate;
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
