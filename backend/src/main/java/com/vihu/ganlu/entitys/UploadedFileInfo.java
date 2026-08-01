package com.vihu.ganlu.entitys;

import lombok.Data;

@Data
public class UploadedFileInfo {
    private String token;
    private String originalName;
    private String extension;
    private String mimeType;
    private String checksum;
    private long size;
    private String purpose;
}
