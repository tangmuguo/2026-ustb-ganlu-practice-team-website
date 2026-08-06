package com.vihu.ganlu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Component
public class FileStorageUtil {
    private final Path uploadRoot;
    private final String activeProfile;

    public static final List<String> IMAGE_EXT = Arrays.asList("jpg", "jpeg", "png", "webp");
    public static final List<String> VIDEO_EXT = Arrays.asList("mp4", "mov");
    public static final List<String> DOC_EXT = Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "zip");
    public static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    public static final long MAX_VIDEO_SIZE = 200 * 1024 * 1024;
    public static final long MAX_DOCUMENT_SIZE = 200 * 1024 * 1024;

    private static final byte[] MAGIC_JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] MAGIC_WEBP_RIFF = "RIFF".getBytes();
    private static final byte[] MAGIC_WEBP_WEBP = "WEBP".getBytes();
    private static final byte[] MAGIC_MP4_FTYP = "ftyp".getBytes();
    private static final byte[] MAGIC_PDF = "%PDF".getBytes();
    private static final byte[] MAGIC_ZIP = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final byte[] MAGIC_OLE = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};
    private static final int MAX_OOXML_ENTRIES = 1000;

    public FileStorageUtil(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${spring.profiles.active:dev}") String activeProfile) {
        this.activeProfile = activeProfile;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(uploadRoot);
            log.info("当前运行环境: {}", this.activeProfile);
            log.info("文件上传目录已初始化: {}", uploadRoot);
            if ("prod".equals(this.activeProfile)) {
                checkDirectoryPermissions();
            }
        } catch (IOException e) {
            throw new StorageException("无法创建上传目录: " + uploadRoot, e);
        }
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    public long getUsableSpace() {
        return getUsableSpace(uploadRoot);
    }

    public long getUsableSpace(Path directory) {
        try {
            return Files.getFileStore(directory.toAbsolutePath().normalize()).getUsableSpace();
        } catch (IOException e) {
            throw new StorageException("无法读取磁盘剩余空间: " + directory, e);
        }
    }

    public Path createDirectory(String relativeDirectory) {
        Path directory = resolveSafe(relativeDirectory);
        try {
            Files.createDirectories(directory);
            return directory;
        } catch (IOException e) {
            throw new StorageException("无法创建目录: " + relativeDirectory, e);
        }
    }

    public String storeFile(MultipartFile file, String subDir) {
        String originalName = safeLeafName(file == null ? null : file.getOriginalFilename());
        return storeFile(file, subDir, extensionOf(originalName));
    }

    public String storeFile(MultipartFile file, String subDir, String forcedExtension) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String originalName = safeLeafName(file.getOriginalFilename());
        String extension = sanitizeExtension(forcedExtension);
        String targetName = UUID.randomUUID().toString()
                + (extension.isEmpty() ? "" : "." + extension);
        Path target = createDirectory(subDir).resolve(targetName).normalize();
        ensureInsideRoot(target);

        Path staging = null;
        try {
            // Staging lives outside /images/**, so a failed or partial copy is never same-origin public.
            staging = Files.createTempFile(uploadRoot, ".upload-", ".tmp");
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, staging, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(staging, target);
            }
            return toRelativePath(target);
        } catch (IOException e) {
            deleteQuietly(staging);
            deleteQuietly(target);
            throw new StorageException("存储文件失败: " + originalName, e);
        } finally {
            deleteQuietly(staging);
        }
    }

    public String moveInto(Path source, String subDir, String extension) {
        ensureExistingSource(source);
        String safeExtension = sanitizeExtension(extension);
        Path target = createDirectory(subDir).resolve(
                UUID.randomUUID().toString() + (safeExtension.isEmpty() ? "" : "." + safeExtension)
        ).normalize();
        ensureInsideRoot(target);
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return toRelativePath(target);
        } catch (IOException e) {
            throw new StorageException("移动文件失败", e);
        }
    }

    public String copyInto(Path source, String subDir, String extension) {
        ensureExistingSource(source);
        String safeExtension = sanitizeExtension(extension);
        Path target = createDirectory(subDir).resolve(
                UUID.randomUUID().toString() + (safeExtension.isEmpty() ? "" : "." + safeExtension)
        ).normalize();
        ensureInsideRoot(target);
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            return toRelativePath(target);
        } catch (IOException e) {
            throw new StorageException("复制文件失败", e);
        }
    }

    /** Allocates a collision-resistant path without creating the file, so recovery intent can be persisted first. */
    public String allocatePath(String subDir, String extension) {
        String safeExtension = sanitizeExtension(extension);
        Path target = createDirectory(subDir).resolve(
                UUID.randomUUID().toString() + (safeExtension.isEmpty() ? "" : "." + safeExtension)
        ).normalize();
        ensureInsideRoot(target);
        if (Files.exists(target)) {
            throw new StorageException("目标文件已存在: " + toRelativePath(target));
        }
        return toRelativePath(target);
    }

    /** Copies into a previously allocated path. The staged source remains available until DB commit succeeds. */
    public void copyToAllocatedPath(Path source, String relativePath) {
        ensureExistingSource(source);
        Path target = loadFile(relativePath);
        if (Files.exists(target)) {
            throw new StorageException("目标文件已存在: " + relativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
        } catch (IOException error) {
            deleteQuietly(target);
            throw new StorageException("复制文件失败: " + relativePath, error);
        }
    }

    public Path moveFile(String fromRelativePath, String toRelativePath) {
        Path source = loadFile(fromRelativePath);
        Path target = loadFile(toRelativePath);
        if (!Files.isRegularFile(source)) {
            throw new StorageException("源文件不存在: " + fromRelativePath);
        }
        if (Files.exists(target)) {
            throw new StorageException("目标文件已存在: " + toRelativePath);
        }
        try {
            Files.createDirectories(target.getParent());
            try {
                return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                return Files.move(source, target);
            }
        } catch (IOException e) {
            throw new StorageException("移动文件失败: " + fromRelativePath + " -> " + toRelativePath, e);
        }
    }

    public Path loadFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        Path resolved = uploadRoot.resolve(relativePath).toAbsolutePath().normalize();
        ensureInsideRoot(resolved);
        return resolved;
    }

    public boolean deleteFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return false;
        }
        Path filePath = loadFile(relativePath);
        try {
            if (!Files.exists(filePath)) {
                log.warn("文件不存在，跳过删除: {}", filePath);
                return false;
            }
            Files.delete(filePath);
            return true;
        } catch (IOException e) {
            throw new StorageException("删除文件失败: " + relativePath, e);
        }
    }

    public void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Path normalized = directory.toAbsolutePath().normalize();
        ensureInsideRoot(normalized);
        if (normalized.equals(uploadRoot)) {
            throw new SecurityException("禁止删除上传根目录");
        }
        try (Stream<Path> paths = Files.walk(normalized)) {
            paths
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new StorageException("清理临时文件失败: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new StorageException("清理临时目录失败: " + normalized, e);
        }
    }

    public String toRelativePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        ensureInsideRoot(normalized);
        return uploadRoot.relativize(normalized).toString().replace('\\', '/');
    }

    public static String safeLeafName(String originalName) {
        String cleaned = StringUtils.cleanPath(originalName == null ? "file" : originalName)
                .replace('\\', '/');
        String leaf = cleaned.substring(cleaned.lastIndexOf('/') + 1).trim();
        if (!StringUtils.hasText(leaf) || ".".equals(leaf) || "..".equals(leaf)) {
            return "file";
        }
        return leaf.replaceAll("[\\r\\n\\t]", "_");
    }

    public static String extensionOf(String filename) {
        String leaf = safeLeafName(filename);
        int dot = leaf.lastIndexOf('.');
        return dot < 0 ? "" : sanitizeExtension(leaf.substring(dot + 1));
    }

    public String extractExtension(String filename) {
        return extensionOf(filename);
    }

    public enum FileCategory {
        IMAGE, VIDEO, DOCUMENT
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

    public ValidatedFile validate(MultipartFile file, long maxSizeBytes) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件为空");
        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) throw new IllegalArgumentException("文件名为空");
        if (file.getSize() > maxSizeBytes) throw new IllegalArgumentException("文件大小超过限制");

        String extension = extractExtension(originalFilename);
        FileCategory category;
        if (IMAGE_EXT.contains(extension)) category = FileCategory.IMAGE;
        else if (VIDEO_EXT.contains(extension)) category = FileCategory.VIDEO;
        else if (DOC_EXT.contains(extension)) category = FileCategory.DOCUMENT;
        else throw new IllegalArgumentException("不支持的文件类型: " + extension);

        byte[] header = new byte[12];
        try (InputStream input = file.getInputStream()) {
            int length = input.read(header);
            if (length < 8) throw new IllegalArgumentException("文件头过短，无法校验");
        } catch (IOException error) {
            throw new IllegalArgumentException("读取文件失败", error);
        }
        if (!matchesMagic(header, category, extension)) {
            throw new IllegalArgumentException("文件内容与实际扩展名不符（魔数校验失败）");
        }
        if (category == FileCategory.DOCUMENT
                && ("docx".equals(extension) || "pptx".equals(extension))
                && !isRealOoxml(file, extension)) {
            throw new IllegalArgumentException("文件不是有效的 " + extension + "（OOXML 结构校验失败）");
        }

        ValidatedFile validated = new ValidatedFile();
        validated.setCategory(category);
        validated.setExtension(extension);
        validated.setMimeType(file.getContentType());
        validated.setSize(file.getSize());
        validated.setRaw(file);
        return validated;
    }

    public ValidatedFile isAllowedImage(MultipartFile file) {
        ValidatedFile validated = validate(file, MAX_IMAGE_SIZE);
        if (validated.getCategory() != FileCategory.IMAGE) {
            throw new IllegalArgumentException("仅支持图片文件");
        }
        return validated;
    }

    public ValidatedFile isAllowedVideo(MultipartFile file) {
        ValidatedFile validated = validate(file, MAX_VIDEO_SIZE);
        if (validated.getCategory() != FileCategory.VIDEO) {
            throw new IllegalArgumentException("仅支持视频文件");
        }
        return validated;
    }

    public ValidatedFile isAllowedDocument(MultipartFile file) {
        ValidatedFile validated = validate(file, MAX_DOCUMENT_SIZE);
        if (validated.getCategory() != FileCategory.DOCUMENT) {
            throw new IllegalArgumentException("仅支持文档文件");
        }
        return validated;
    }

    private boolean matchesMagic(byte[] header, FileCategory category, String extension) {
        switch (category) {
            case IMAGE:
                if ("jpg".equals(extension) || "jpeg".equals(extension)) return startsWith(header, MAGIC_JPEG);
                if ("png".equals(extension)) return startsWith(header, MAGIC_PNG);
                return "webp".equals(extension)
                        && startsWith(header, MAGIC_WEBP_RIFF)
                        && matchBytes(header, 8, MAGIC_WEBP_WEBP);
            case VIDEO:
                return matchBytes(header, 4, MAGIC_MP4_FTYP);
            case DOCUMENT:
                if ("pdf".equals(extension)) return startsWith(header, MAGIC_PDF);
                if ("docx".equals(extension) || "pptx".equals(extension) || "zip".equals(extension)) {
                    return startsWith(header, MAGIC_ZIP);
                }
                return startsWith(header, MAGIC_OLE);
            default:
                return false;
        }
    }

    private boolean isRealOoxml(MultipartFile file, String extension) {
        String contentPrefix = "docx".equals(extension) ? "word/" : "ppt/";
        boolean hasContentTypes = false;
        boolean hasContentDirectory = false;
        try (InputStream input = file.getInputStream();
             java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(input)) {
            java.util.zip.ZipEntry entry;
            int count = 0;
            while ((entry = zip.getNextEntry()) != null && ++count <= MAX_OOXML_ENTRIES) {
                String name = entry.getName();
                if (name != null && !name.contains("..")) {
                    hasContentTypes |= "[Content_Types].xml".equals(name);
                    hasContentDirectory |= name.startsWith(contentPrefix);
                }
                if (hasContentTypes && hasContentDirectory) return true;
                zip.closeEntry();
            }
        } catch (IOException ignored) {
            return false;
        }
        return false;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return matchBytes(data, 0, prefix);
    }

    private boolean matchBytes(byte[] data, int offset, byte[] pattern) {
        if (data.length < offset + pattern.length) return false;
        for (int index = 0; index < pattern.length; index++) {
            if (data[offset + index] != pattern[index]) return false;
        }
        return true;
    }

    private Path resolveSafe(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("相对目录不能为空");
        }
        Path resolved = uploadRoot.resolve(relativePath).toAbsolutePath().normalize();
        ensureInsideRoot(resolved);
        return resolved;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(uploadRoot)) {
            throw new StorageException("非法文件路径: " + path);
        }
    }

    private void ensureExistingSource(Path source) {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("源文件不存在");
        }
        ensureInsideRoot(source.toAbsolutePath().normalize());
    }

    private static String sanitizeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        String safe = extension.toLowerCase().replaceAll("[^a-z0-9]", "");
        return safe.length() > 10 ? safe.substring(0, 10) : safe;
    }

    private void checkDirectoryPermissions() throws IOException {
        Path testFile = uploadRoot.resolve(".permission-test");
        try {
            Files.createFile(testFile);
            Files.delete(testFile);
        } catch (AccessDeniedException e) {
            throw new IOException("上传目录不可写: " + uploadRoot, e);
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupError) {
            log.warn("清理上传暂存文件失败: {}", path, cleanupError);
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message) {
            super(message);
        }

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
