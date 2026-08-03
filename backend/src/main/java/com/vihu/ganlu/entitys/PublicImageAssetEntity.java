package com.vihu.ganlu.entitys;

import lombok.Data;

@Data
public class PublicImageAssetEntity {
    private String relativePath;
    private Integer ownerUserId;
    private Long fileSize;
}
