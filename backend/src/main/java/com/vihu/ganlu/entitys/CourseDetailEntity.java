package com.vihu.ganlu.entitys;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.Date;

@Data
public class CourseDetailEntity {
    private Integer id;
    private String title;
    private Integer courseType;
    private Integer uploaderUserId;
    private String uploaderName;
    private String author;
    private String thumbnailUrl;
    private Integer courseId;
    private String customSubject;
    private Integer year;
    @JsonIgnore
    private String originalFilePath;
    @JsonIgnore
    private String previewFilePath;
    private String originalFilename;
    @JsonIgnore
    private String files;
    private Long fileSize;
    private String fileType;
    private String fileExtension;
    private String mimeType;
    private String previewStatus;
    private String scanStatus;
    private String scanDiagnosticStatus;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private String courseName;
    private String previewUrl;
    private String downloadUrl;

    // 关联的课程信息
    private CourseEntity course;
}
