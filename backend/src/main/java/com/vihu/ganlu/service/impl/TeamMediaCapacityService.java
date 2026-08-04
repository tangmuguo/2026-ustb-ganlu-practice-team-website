package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class TeamMediaCapacityService {
    private final FileStorageUtil fileStorageUtil;
    private final Path multipartTempDirectory;
    private final long uploadReserveBytes;
    private final long multipartReserveBytes;

    public TeamMediaCapacityService(
            FileStorageUtil fileStorageUtil,
            @Value("${spring.servlet.multipart.location:}") String multipartLocation,
            @Value("${team.media.upload-min-free-disk-mb:1024}") long uploadReserveMb,
            @Value("${team.media.multipart-min-free-disk-mb:1024}") long multipartReserveMb) {
        this.fileStorageUtil = fileStorageUtil;
        String location = multipartLocation == null ? "" : multipartLocation.trim();
        this.multipartTempDirectory = Paths.get(location.isEmpty()
                ? System.getProperty("java.io.tmpdir") : location).toAbsolutePath().normalize();
        this.uploadReserveBytes = Math.max(0L, uploadReserveMb) * 1024L * 1024L;
        this.multipartReserveBytes = Math.max(0L, multipartReserveMb) * 1024L * 1024L;
    }

    /**
     * 过滤器在 Multipart 解析前调用，Service 在正式复制前再次调用。
     */
    public void ensureCapacity(long incomingBytes) {
        if (incomingBytes <= 0) throw new IllegalArgumentException("上传文件大小不正确");
        ensureDirectory(multipartTempDirectory);
        requireFreeSpace(fileStorageUtil.getUploadRoot(), uploadReserveBytes, incomingBytes, "正式附件目录");
        requireFreeSpace(multipartTempDirectory, multipartReserveBytes, incomingBytes, "Multipart 临时目录");
    }

    public Path getMultipartTempDirectory() {
        return multipartTempDirectory;
    }

    private void requireFreeSpace(Path directory, long reserveBytes, long incomingBytes, String label) {
        long usable = fileStorageUtil.getUsableSpace(directory);
        if (usable < incomingBytes || usable - incomingBytes < reserveBytes) {
            throw new IllegalStateException(label + "剩余空间不足，已拒绝本次上传");
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception error) {
            throw new FileStorageUtil.StorageException("无法初始化 Multipart 临时目录", error);
        }
    }
}
