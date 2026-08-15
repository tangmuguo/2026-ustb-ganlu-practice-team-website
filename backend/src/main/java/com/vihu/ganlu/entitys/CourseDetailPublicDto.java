package com.vihu.ganlu.entitys;

import lombok.Data;

import java.util.Date;

/**
 * Whitelist projection for the public course-material endpoints.
 *
 * <p>CourseDetailEntity also carries storage paths and historical identity
 * fields needed by the service layer.  Those fields intentionally do not
 * belong in this DTO.</p>
 */
@Data
public class CourseDetailPublicDto {
    private Integer id;
    private String title;
    private Integer courseType;
    private String uploaderName;
    private String thumbnailUrl;
    private Integer courseId;
    private String customSubject;
    private Integer year;
    private String originalFilename;
    private Long fileSize;
    private String fileExtension;
    private String mimeType;
    private String previewStatus;
    private Integer status;
    private Date createTime;
    private Date updateTime;
    private String courseName;
    private String previewUrl;
    private String downloadUrl;

    public static CourseDetailPublicDto from(CourseDetailEntity material) {
        if (material == null) {
            return null;
        }
        CourseDetailPublicDto dto = new CourseDetailPublicDto();
        dto.id = material.getId();
        dto.title = material.getTitle();
        dto.courseType = material.getCourseType();
        dto.uploaderName = material.getUploaderName();
        dto.thumbnailUrl = material.getThumbnailUrl();
        dto.courseId = material.getCourseId();
        dto.customSubject = material.getCustomSubject();
        dto.year = material.getYear();
        dto.originalFilename = material.getOriginalFilename();
        dto.fileSize = material.getFileSize();
        dto.fileExtension = material.getFileExtension();
        dto.mimeType = material.getMimeType();
        dto.previewStatus = material.getPreviewStatus();
        dto.status = material.getStatus();
        dto.createTime = material.getCreateTime();
        dto.updateTime = material.getUpdateTime();
        dto.courseName = material.getCourseName();
        dto.previewUrl = material.getPreviewUrl();
        dto.downloadUrl = material.getDownloadUrl();
        return dto;
    }
}
