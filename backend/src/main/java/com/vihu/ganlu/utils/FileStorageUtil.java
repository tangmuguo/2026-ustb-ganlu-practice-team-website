package com.vihu.ganlu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Slf4j
@Component
public class FileStorageUtil {
    private final Path uploadRoot;
    private final String activeProfile;

    public FileStorageUtil(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${spring.profiles.active:dev}") String activeProfile) {

        this.activeProfile = activeProfile;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadRoot);
            log.info("当前运行环境: {}", this.activeProfile);
            log.info("文件上传目录已初始化:{}",uploadRoot);

            // 生产环境额外检查权限
            if ("prod".equals(this.activeProfile)) {
                checkDirectoryPermissions();
            }
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadRoot, e);
        }
    }

    private void checkDirectoryPermissions() throws IOException {
        Path testFile = uploadRoot.resolve(".permission-test");
        try {
            Files.createFile(testFile);
            Files.delete(testFile);
        } catch (AccessDeniedException e) {
            String errorMsg = String.format(
                    "上传目录权限不足。请执行: sudo chown -R tomcat8:tomcat8 %s",
                    uploadRoot.getParent());
            throw new IOException(errorMsg, e);
        }
    }

    public String storeFile(MultipartFile file, String subDir) {
        try {
            Path targetDir = uploadRoot.resolve(subDir);
            Files.createDirectories(targetDir);

            String filename = StringUtils.cleanPath(
                    UUID.randomUUID() + "_" + file.getOriginalFilename());

            Path targetPath = targetDir.resolve(filename);
            file.transferTo(targetPath);

            return Paths.get(subDir, filename).toString();
        } catch (IOException e) {
            throw new StorageException("存储文件失败: " + file.getOriginalFilename(), e);
        }
    }

    /**
     * 由相对路径relativePath与uploadRoot的根路径 组合成完整的绝对路径，多用于读取或者下载文件
     * @param relativePath
     * @return
     */
    public Path loadFile(String relativePath) {
        return uploadRoot.resolve(relativePath).normalize();
    }

    /**
     * 根据相对路径删除文件
     * @param relativePath 要删除文件的相对路径
     * @return 是否删除成功
     * @throws StorageException 如果删除过程中出现错误
     */
    public boolean deleteFile(String relativePath) {
        try {
            Path filePath = loadFile(relativePath);

            // 安全检查：确保文件路径在uploadRoot目录下
            if (!filePath.startsWith(uploadRoot)) {
                throw new SecurityException("尝试删除不在上传目录下的文件: " + filePath);
            }

            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                log.warn("文件不存在，无法删除: {}", filePath);
                return false;
            }

            // 删除文件
            Files.delete(filePath);
            log.info("文件删除成功: {}", filePath);

            // 可选：删除空目录
            //deleteEmptyParentDirectories(filePath.getParent());

            return true;
        } catch (IOException e) {
            throw new StorageException("删除文件失败: " + relativePath, e);
        }
    }

    /**
     * 递归删除空的父目录
     * @param directory 要检查的目录
     * @throws IOException 如果删除过程中出现错误
     */
    private void deleteEmptyParentDirectories(Path directory) throws IOException {
        // 确保目录在uploadRoot下
        if (directory != null && directory.startsWith(uploadRoot) && !directory.equals(uploadRoot)) {
            // 检查目录是否为空
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
                if (!dirStream.iterator().hasNext()) {
                    // 目录为空，删除它
                    Files.delete(directory);
                    log.info("已删除空目录: {}", directory);

                    // 递归检查上级目录
                    deleteEmptyParentDirectories(directory.getParent());
                }
            }
        }
    }

    // 自定义异常
    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }


}
