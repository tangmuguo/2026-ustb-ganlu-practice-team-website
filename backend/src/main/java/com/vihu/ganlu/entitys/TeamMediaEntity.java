package com.vihu.ganlu.entitys;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class TeamMediaEntity {
    Integer id;
    String filename;
    String relativePath;
    String mimeType;
    Long fileSize;
    Integer uploaderId;
    Integer teamId;
    String relatedType;
    Integer relatedId;
    String status;
    /** PENDING until the security scanner returns CLEAN; never trust status alone. */
    String scanStatus;
    String scanDiagnosticStatus;
    String rejectReason;
    Timestamp createdAt;
    Timestamp updatedAt;
}
