package com.vihu.ganlu.entitys;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublicImageMigrationIssue {
    private String code;
    private String source;
    private String path;
    private String message;
}
