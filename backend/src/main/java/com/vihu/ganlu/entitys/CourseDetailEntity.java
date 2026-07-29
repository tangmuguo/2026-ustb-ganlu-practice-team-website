package com.vihu.ganlu.entitys;

import lombok.Data;
import java.util.Date;

@Data
public class CourseDetailEntity {
    private Integer id;
    private String title;
    private Integer courseType;
    private String author;
    private String thumbnailUrl;
    private Integer courseId;
    private String files;
    private Long fileSize;
    private String fileType;
    private Date createTime;
    private Date updateTime;
    private String courseName;

    // 关联的课程信息
    private CourseEntity course;
}
