package com.vihu.ganlu.entitys;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class FileDeletionTaskEntity {
    private Long id;
    private String assetType;
    private Long assetId;
    private String relativePath;
    private Integer ownerUserId;
    private Long fileSize;
    private String status;
    private Integer retryCount;
    private String lastError;
    private Timestamp nextRetryAt;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
