package com.vihu.ganlu.entitys;

import lombok.Data;

@Data
public class MaterialCreateRequest {
    private String title;
    private Integer courseType;
    private Integer courseId;
    private String customSubject;
    private Integer year;
    private String coverToken;
    private String fileToken;
}
