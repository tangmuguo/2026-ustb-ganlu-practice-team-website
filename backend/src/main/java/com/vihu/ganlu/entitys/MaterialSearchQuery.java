package com.vihu.ganlu.entitys;

import lombok.Data;

@Data
public class MaterialSearchQuery {
    private String keyword;
    private Integer courseType;
    private Integer courseId;
    private Integer year;
    private Integer minYear;
    private Integer maxYear;
    private Integer page = 1;
    private Integer pageSize = 10;
}
