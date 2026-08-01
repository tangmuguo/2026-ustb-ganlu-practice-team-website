package com.vihu.ganlu.service;

import com.github.pagehelper.PageInfo;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.MaterialCreateRequest;
import com.vihu.ganlu.entitys.MaterialSearchQuery;
import com.vihu.ganlu.entitys.UploadedFileInfo;
import com.vihu.ganlu.entitys.UserEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface CourseDetailService {
    PageInfo<CourseDetailEntity> search(MaterialSearchQuery query);

    CourseDetailEntity getCourseById(int id);

    CourseDetailEntity createMaterial(MaterialCreateRequest request, UserEntity uploader) throws IOException;

    boolean deleteCourseById(int id);

    String saveChunk(MultipartFile chunk, int chunkNumber, int totalChunks, String identifier,
                     String filename, long expectedSize, String purpose, int userId) throws IOException;

    UploadedFileInfo mergeChunks(String filename, String identifier, int totalChunks,
                                 long expectedSize, String purpose, int userId) throws IOException;

    Map<String, Object> checkFileExist(String fileMd5, String purpose, int userId) throws IOException;

    Path getDownloadPath(int id);
}
