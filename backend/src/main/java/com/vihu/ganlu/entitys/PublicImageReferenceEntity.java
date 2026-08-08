package com.vihu.ganlu.entitys;

import lombok.Data;

@Data
public class PublicImageReferenceEntity {
    private String sourceType;
    private Integer sourceId;
    private String relativePath;
    private Integer ownerHint;
}
