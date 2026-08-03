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
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

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
            throw new SecurityException("非法文件路径: " + path);
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
