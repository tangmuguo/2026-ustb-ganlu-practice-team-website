package com.vihu.ganlu.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.mappers.CourseDetailMapper;
import com.vihu.ganlu.service.CourseDetailService;
import com.vihu.ganlu.utils.FileStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.io.Files.getFileExtension;

@Slf4j
@Service
public class CourseDetailServiceImpl implements CourseDetailService {
    @Resource
    CourseDetailMapper courseDetailMapper;
    @Resource
    FileStorageUtil fileStorageUtil;

    @Value("${file.upload-dir}")
    private String uploadDir;
    @Value("${file.chunk-dir}")
    private String chunkDir;
    @Value("${file.materials-dir}")
    private String materialsDir;
    @Value("${file.images-dir}")
    private String imagesDir;

    private static final String CHUNK_DIR = "/tmp/upload_chunks/";
    private static final String FINAL_DIR = "/upload/files/";

    // 在服务层保存分片信息
    private final Map<String, Set<Integer>> receivedChunks = new ConcurrentHashMap<>();

    @Override
    public int insertCourseDetail(CourseDetailEntity entity) {
        return courseDetailMapper.insertCourseDetail(entity);
    }

    @Override
    public List<CourseDetailEntity> findAllCourse() {
        return courseDetailMapper.findAllCourse();
    }

    @Override
    public PageInfo<CourseDetailEntity> getCourseList(Integer page, Integer size) {
        PageHelper.startPage(page, size);
        List<CourseDetailEntity> materials = courseDetailMapper.findCourseList();
        return new PageInfo<CourseDetailEntity>(materials);
    }

    @Override
    public boolean uploadCourseMaterial(CourseDetailEntity courseDetail, MultipartFile imageFile, MultipartFile courseFile) {
        try {
            // 1. 保存缩略图
            String thumbnailPath = fileStorageUtil.storeFile(imageFile, "images");
            // 2. 保存课件文件
            String filePath = fileStorageUtil.storeFile(courseFile, "materials");

            // 3. 设置实体属性
            courseDetail.setThumbnailUrl(thumbnailPath);
            courseDetail.setFiles(filePath);
            courseDetail.setFileSize(courseFile.getSize());
            courseDetail.setFileType(courseFile.getContentType());

            // 4. 插入数据库
            return courseDetailMapper.insertCourseDetail(courseDetail) > 0;
        } catch (RuntimeException e) {
            throw new RuntimeException("文件存储失败", e);
        }
    }




    public String uploadImage(MultipartFile imageFile){
        try{
            String thumbnailPath = fileStorageUtil.storeFile(imageFile, "images");
            return thumbnailPath;

        }catch (RuntimeException e) {
            throw new RuntimeException("文件存储失败", e);
        }
    }

    public String uploadCourseFile(MultipartFile courseFile){
        try{
            String filePath = fileStorageUtil.storeFile(courseFile, "materials");
            return filePath;

        }catch (RuntimeException e) {
            throw new RuntimeException("文件存储失败", e);
        }
    }

    @Override
    public CourseDetailEntity getCourseById(int id) {
        return courseDetailMapper.getCourseById(id);
    }

    @Override
    public int deleteCourseById(int id) {
        CourseDetailEntity courseById = courseDetailMapper.getCourseById(id);
        fileStorageUtil.deleteFile(courseById.getFiles());
        fileStorageUtil.deleteFile(courseById.getThumbnailUrl());

        return courseDetailMapper.deleteCourseById(id);
    }

    @Override
    public String saveChunk(MultipartFile chunk, int chunkNumber, int totalChunks,String identifier) throws IOException {
        // 记录已接收的分片
        receivedChunks.computeIfAbsent(identifier, k -> new HashSet<>())
                .add(chunkNumber);
        // 创建分片临时目录
        File chunkDirectory = new File(chunkDir + File.separator + identifier);
        if (!chunkDirectory.exists()) {
            chunkDirectory.mkdirs();
        }

        // 保存分片
        String chunkPath = chunkDirectory.getPath() + File.separator + chunkNumber;
        File dest = new File(chunkPath);
        chunk.transferTo(dest);
        // 可以在这里检查是否已收到全部分片
        if(receivedChunks.get(identifier).size() == totalChunks) {
            log.info("文件 {} 的所有分片已上传完成", identifier);
        }
        return chunkPath;
    }

    @Override
    public String mergeChunks(String filename, String identifier) throws IOException {
        // 1. 生成最终文件名
        String safeFilename = generateSafeFilename(filename, identifier);

        File chunkDirectory = new File(chunkDir + File.separator + identifier);
        File[] chunks = chunkDirectory.listFiles();

        if (chunks == null || chunks.length == 0) {
            throw new IOException("没有找到分片文件");
        }

        // 按分片序号排序
        Arrays.sort(chunks, Comparator.comparingInt(f -> Integer.parseInt(f.getName())));
        // 根据文件类型确定存储目录
        String fileExtension = getFileExtension(filename).toLowerCase();
        boolean isImage = isImageFile(fileExtension);

        String targetDir = isImage ? imagesDir : materialsDir;
        String relativePath = isImage ? "images/" + safeFilename : "materials/" + safeFilename;
        String absolutePath = targetDir + File.separator + safeFilename;

        // 确保最终目录存在
        File finalDir = new File(FINAL_DIR);
        if (!finalDir.exists()) {
            finalDir.mkdirs();
        }

        try (OutputStream out = new FileOutputStream(absolutePath)) {
            for (File chunk : chunks) {
                Files.copy(chunk.toPath(), out);
                chunk.delete(); // 合并后删除分片
            }
        }

        // 删除临时目录
        deleteDirectory(chunkDirectory);
        return relativePath; // 返回相对路径
    }

    @Override
    public Map<String, Object> checkFileExist(String fileMd5) {
        Map<String, Object> result = new HashMap<>();
        String md5Prefix = fileMd5.substring(0, 8); // 只使用前8位匹配

        // 1. 检查已合并的文件
        String[] dirsToCheck = {imagesDir, materialsDir};
        for (String dir : dirsToCheck) {
            File directory = new File(dir);
            if (directory.exists()) {
                // 优化后的文件名匹配逻辑
                File[] files = directory.listFiles((d, name) -> {
                    // 匹配格式：*_MD5前8位_*.ext
                    int underscore1 = name.lastIndexOf('_');
                    int underscore2 = name.lastIndexOf('_', underscore1 - 1);

                    if (underscore2 != -1) {
                        String extractedMd5 = name.substring(underscore2 + 1, underscore1);
                        return extractedMd5.equals(md5Prefix);
                    }
                    return false;
                });

                if (files != null && files.length > 0) {
                    result.put("exist", true);
                    result.put("path", dir.equals(imagesDir) ?
                            "images/" + files[0].getName() :
                            "materials/" + files[0].getName());
                    return result;
                }
            }
        }

        // 2. 检查分片目录（保持原逻辑）
        File chunkDir = new File(this.chunkDir, fileMd5); // 分片目录仍用完整MD5
        if (chunkDir.exists()) {
            File[] chunks = chunkDir.listFiles();
            if (chunks != null && chunks.length > 0) {
                result.put("exist", false);
                result.put("uploadedChunks", chunks.length);
                return result;
            }
        }

        // 3. 文件不存在
        result.put("exist", false);
        return result;
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        if (!directory.delete()) {
            throw new IOException("无法删除目录: " + directory);
        }
    }

    // 获取文件扩展名
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }

    // 判断是否为图片文件
    private boolean isImageFile(String extension) {
        Set<String> imageExtensions = new HashSet<>(Arrays.asList(
                "jpg", "jpeg", "png", "gif", "bmp", "webp"
        ));
        return imageExtensions.contains(extension);
    }

    private String generateSafeFilename(String originalFilename, String fileMd5) {
        String leafName = originalFilename.replace('\\', '/');
        leafName = leafName.substring(leafName.lastIndexOf('/') + 1);
        int dotIndex = leafName.lastIndexOf('.');
        String name = (dotIndex <= 0) ? leafName : leafName.substring(0, dotIndex);
        String ext = (dotIndex <= 0) ? "" : leafName.substring(dotIndex + 1)
                .replaceAll("[^A-Za-z0-9]", "");
        if (ext.length() > 10) {
            ext = ext.substring(0, 10);
        }

        // 新正则表达式：过滤特殊字符,保留中文、日文、韩文字符，字母数字和常用符号
        String cleanName = name.replaceAll("[^\\p{L}\\p{Nd}\\- _]", "_").trim();
        if (cleanName.isEmpty()) {
            cleanName = "file";
        }

        return String.format("%s_%s_%s%s",
                cleanName,
                fileMd5.substring(0, 8),
                UUID.randomUUID().toString().substring(0, 4),
                ext.isEmpty() ? "" : "." + ext);
    }
}
