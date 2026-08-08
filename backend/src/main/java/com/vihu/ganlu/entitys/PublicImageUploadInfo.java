package com.vihu.ganlu.entitys;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublicImageUploadInfo {
    private String token;
    private String originalName;
    private String extension;
    private String contentType;
    private long size;
}
