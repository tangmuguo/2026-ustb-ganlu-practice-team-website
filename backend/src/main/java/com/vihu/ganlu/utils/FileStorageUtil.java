package com.vihu.ganlu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class FileStorageUtil {
    private final Path uploadRoot;
    private final String activeProfile;

    // ---- 文件类型白名单 ----
    public static final List<String> IMAGE_EXT = Arrays.asList("jpg", "jpeg", "png", "webp");
    public static final List<String> VIDEO_EXT = Arrays.asList("mp4", "mov");
    public static final List<String> DOC_EXT = Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "zip");

    // ---- 魔数签名 ----
    private static final byte[] MAGIC_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_WEBP_RIFF = "RIFF".getBytes();
    private static final byte[] MAGIC_WEBP_WEBP = "WEBP".getBytes();
    private static final byte[] MAGIC_MP4_FTYP = "ftyp".getBytes();
    private static final byte[] MAGIC_PDF = "%PDF".getBytes();

    // ---- 大小限制 ----
    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;      // 10MB
    public static final long MAX_VIDEO_SIZE = 200 * 1024 * 1024;     // 200MB
    public static final long MAX_DOCUMENT_SIZE = 200 * 1024 * 1024;  // 200MB

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

    // =====================================================================
    // 文件校验增强
    // =====================================================================

    public enum FileCategory {
        IMAGE, VIDEO, DOCUMENT, REJECTED
    }

    public static class ValidatedFile {
        private FileCategory category;
        private String extension;
        private String mimeType;
        private long size;
        private MultipartFile raw;

        public FileCategory getCategory() { return category; }
        public void setCategory(FileCategory category) { this.category = category; }
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
        public MultipartFile getRaw() { return raw; }
        public void setRaw(MultipartFile raw) { this.raw = raw; }
    }

    /**
     * 综合校验文件：扩展名 + MIME + 魔数 + 大小。
     * @param file 上传文件
     * @param maxSizeBytes 允许的最大字节数
     * @return 校验通过的文件信息
     * @throws IllegalArgumentException 校验失败时抛出
     */
    public ValidatedFile validate(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("文件名为空");
        }

        // 1. 提取扩展名
        String ext = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0 && dot < originalFilename.length() - 1) {
            ext = originalFilename.substring(dot + 1).toLowerCase();
        }

        // 2. 大小校验
        if (file.getSize() > maxSizeBytes) {
            throw new IllegalArgumentException(
                    String.format("文件大小超过限制: %d > %d bytes", file.getSize(), maxSizeBytes));
        }

        // 3. 确定类别 + 校验扩展名白名单
        FileCategory category;
        if (IMAGE_EXT.contains(ext)) {
            category = FileCategory.IMAGE;
        } else if (VIDEO_EXT.contains(ext)) {
            category = FileCategory.VIDEO;
        } else if (DOC_EXT.contains(ext)) {
            category = FileCategory.DOCUMENT;
        } else {
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
        }

        // 4. 魔数校验（防伪装）
        try {
            byte[] header = file.getBytes();
            if (header.length < 8) {
                throw new IllegalArgumentException("文件头过短，无法校验");
            }
            if (!matchesMagic(header, category, ext)) {
                throw new IllegalArgumentException("文件内容与实际扩展名不符（魔数校验失败）");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败: " + e.getMessage());
        }

        ValidatedFile vf = new ValidatedFile();
        vf.setCategory(category);
        vf.setExtension(ext);
        vf.setMimeType(file.getContentType());
        vf.setSize(file.getSize());
        vf.setRaw(file);
        return vf;
    }

    private boolean matchesMagic(byte[] header, FileCategory category, String ext) {
        switch (category) {
            case IMAGE:
                if ("jpg".equals(ext) || "jpeg".equals(ext)) {
                    return startsWith(header, MAGIC_JPEG);
                } else if ("png".equals(ext)) {
                    return startsWith(header, MAGIC_PNG);
                } else if ("webp".equals(ext)) {
                    // RIFF....WEBP
                    return startsWith(header, MAGIC_WEBP_RIFF)
                            && header.length >= 12
                            && matchBytes(header, 8, MAGIC_WEBP_WEBP);
                }
                return false;
            case VIDEO:
                // MP4/MOV: ftyp box at offset 4
                if (header.length >= 12) {
                    return matchBytes(header, 4, MAGIC_MP4_FTYP);
                }
                return false;
            case DOCUMENT:
                if ("pdf".equals(ext)) {
                    return startsWith(header, MAGIC_PDF);
                }
                // doc/docx/ppt/pptx/zip 均为 OOXML/OLE 格式，魔数较复杂，
                // 这里仅做扩展名校验（后续可补充）
                return true;
            default:
                return false;
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private boolean matchBytes(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) return false;
        for (int i = 0; i < pattern.length; i++) {
            if (data[offset + i] != pattern[i]) return false;
        }
        return true;
    }

    // ---- 便捷方法 ----
    public ValidatedFile isAllowedImage(MultipartFile file) {
        return validate(file, MAX_IMAGE_SIZE);
    }

    public ValidatedFile isAllowedVideo(MultipartFile file) {
        return validate(file, MAX_VIDEO_SIZE);
    }

    public ValidatedFile isAllowedDocument(MultipartFile file) {
        return validate(file, MAX_DOCUMENT_SIZE);
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
