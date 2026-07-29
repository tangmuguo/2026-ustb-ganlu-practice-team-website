package com.vihu.ganlu.service;

import com.github.pagehelper.PageInfo;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface CourseDetailService {
    boolean uploadCourseMaterial(CourseDetailEntity courseDetail, MultipartFile imageFile, MultipartFile courseFile);
    int insertCourseDetail(CourseDetailEntity entity);
    List<CourseDetailEntity> findAllCourse();
    public PageInfo<CourseDetailEntity> getCourseList(Integer page, Integer size);
    String uploadImage(MultipartFile imageFile);
    String uploadCourseFile(MultipartFile courseFile);
    CourseDetailEntity getCourseById(int id);
    int deleteCourseById(int id);

    // 新增分片上传方法
    String saveChunk(MultipartFile chunk, int chunkNumber, int totalChunks,String identifier) throws IOException;
    String mergeChunks(String filename, String identifier) throws IOException;
    Map<String, Object> checkFileExist(String fileMd5);
}
