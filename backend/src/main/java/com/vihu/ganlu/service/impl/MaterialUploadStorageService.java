package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.UploadedFileInfo;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.MaterialFileValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class MaterialUploadStorageService {
    public static final long CHUNK_SIZE = 5L * 1024L * 1024L;

    private static final String CHUNK_ROOT = "temp_chunks";
    private static final String STAGING_ROOT = "staging/materials";
    private static final String SESSION_METADATA = "session.properties";
    private static final String INDEX_DIRECTORY = ".index";

    private final FileStorageUtil fileStorageUtil;
    private final MaterialFileValidator fileValidator;
    private final long sessionTtlMillis;
    private final long stagingTtlMillis;
    private final long userQuotaBytes;
    private final int maxActiveSessionsPerUser;
    private final Map<Integer, Object> userLocks = new ConcurrentHashMap<>();

    public MaterialUploadStorageService(
            FileStorageUtil fileStorageUtil,
            MaterialFileValidator fileValidator,
            @Value("${material.upload.session-ttl-hours:24}") long sessionTtlHours,
            @Value("${material.upload.staging-ttl-hours:24}") long stagingTtlHours,
            @Value("${material.upload.user-temp-quota-mb:1024}") long userQuotaMegabytes,
            @Value("${material.upload.max-active-sessions-per-user:5}") int maxActiveSessionsPerUser) {
        this.fileStorageUtil = fileStorageUtil;
        this.fileValidator = fileValidator;
        this.sessionTtlMillis = Duration.ofHours(Math.max(1L, sessionTtlHours)).toMillis();
        this.stagingTtlMillis = Duration.ofHours(Math.max(1L, stagingTtlHours)).toMillis();
        this.userQuotaBytes = Math.max(MaterialFileValidator.MAX_MATERIAL_SIZE,
                userQuotaMegabytes * 1024L * 1024L);
        this.maxActiveSessionsPerUser = Math.max(1, maxActiveSessionsPerUser);
    }

    public String saveChunk(MultipartFile chunk, int chunkNumber, int totalChunks, String identifier,
                            String filename, long expectedSize, String purpose, int userId) throws IOException {
        requireUserId(userId);
        String normalizedPurpose = fileValidator.normalizePurpose(purpose);
        String safeFilename = FileStorageUtil.safeLeafName(filename);
        validateChunkGeometry(chunk, chunkNumber, totalChunks, expectedSize);

        return withUserLock(userId, () -> {
            long now = System.currentTimeMillis();
            cleanupUserExpiredNoLock(userId, now);
            Path directory = createDirectory(chunkDirectoryName(userId, normalizedPurpose, identifier));
            Path metadata = directory.resolve(SESSION_METADATA);
            UploadSession session;
            if (Files.isRegularFile(metadata)) {
                session = readSession(metadata);
                session.requireMatches(userId, normalizedPurpose, identifier, safeFilename, expectedSize, totalChunks);
            } else {
                if (countActiveSessionsNoLock(userId) >= maxActiveSessionsPerUser) {
                    throw new IllegalStateException("同时进行的上传任务过多，请完成或取消已有上传");
                }
                session = new UploadSession(userId, normalizedPurpose, identifier.toLowerCase(), safeFilename,
                        expectedSize, totalChunks, now, now);
                writeSession(metadata, session);
            }

            Path target = directory.resolve(chunkNumber + ".part").normalize();
            long existingTargetSize = Files.isRegularFile(target) ? Files.size(target) : 0L;
            long accumulatedSize = partBytes(directory, target);
            if (accumulatedSize + chunk.getSize() > expectedSize) {
                throw new IllegalArgumentException("分片累计大小超过声明的文件大小");
            }
            long currentUsage = userTemporaryUsageNoLock(userId);
            if (currentUsage - existingTargetSize + chunk.getSize() > userQuotaBytes) {
                throw new IllegalStateException("当前账号的临时上传空间已达到上限");
            }

            Path temporary = directory.resolve(chunkNumber + ".uploading-" + UUID.randomUUID()).normalize();
            try (InputStream input = chunk.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(temporary) != chunk.getSize()) {
                Files.deleteIfExists(temporary);
                throw new IOException("分片写入不完整");
            }
            moveAtomically(temporary, target);
            session.lastActivity = now;
            writeSession(metadata, session);
            return String.valueOf(chunkNumber);
        });
    }

    public UploadedFileInfo mergeChunks(String filename, String identifier, int totalChunks,
                                         long expectedSize, String purpose, int userId) throws IOException {
        requireUserId(userId);
        String normalizedPurpose = fileValidator.normalizePurpose(purpose);
        String safeFilename = FileStorageUtil.safeLeafName(filename);
        return withUserLock(userId, () -> {
            long now = System.currentTimeMillis();
            cleanupUserExpiredNoLock(userId, now);
            UploadedFileInfo existing = findStagedNoLock(userId, normalizedPurpose, identifier, now);
            if (existing != null) {
                return existing;
            }

            Path directory = resolve(chunkDirectoryName(userId, normalizedPurpose, identifier));
            Path metadata = directory.resolve(SESSION_METADATA);
            if (!Files.isRegularFile(metadata)) {
                throw new IllegalStateException("上传会话不存在或已过期，请重新上传");
            }
            UploadSession session = readSession(metadata);
            session.requireMatches(userId, normalizedPurpose, identifier, safeFilename, expectedSize, totalChunks);
            verifyAllParts(directory, session);

            if (userTemporaryUsageNoLock(userId) + expectedSize > userQuotaBytes) {
                throw new IllegalStateException("合并文件所需的临时空间不足，请取消其他上传后重试");
            }
            Path merged = directory.resolve(identifier.toLowerCase() + ".merge").normalize();
            try {
                try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(merged))) {
                    for (int chunkNumber = 1; chunkNumber <= totalChunks; chunkNumber++) {
                        Files.copy(directory.resolve(chunkNumber + ".part"), output);
                    }
                }

                UploadedFileInfo info = fileValidator.validate(
                        merged, safeFilename, normalizedPurpose, expectedSize);
                if (!identifier.equalsIgnoreCase(info.getChecksum())) {
                    throw new IllegalArgumentException("文件 MD5 校验失败");
                }

                String token = UUID.randomUUID().toString();
                info.setToken(token);
                Path stagingDirectory = createDirectory(stagingDirectoryName(userId, normalizedPurpose));
                Path stagedFile = stagingDirectory.resolve(token + "." + info.getExtension()).normalize();
                Files.move(merged, stagedFile, StandardCopyOption.REPLACE_EXISTING);
                Path stagedMetadata = stagingDirectory.resolve(token + ".properties");
                writeStagedMetadata(stagedMetadata, info, now, now,
                        Files.getLastModifiedTime(stagedFile).toMillis());
                writeIndex(indexPath(stagingDirectory, identifier), token);
                fileStorageUtil.deleteTree(directory);
                return info;
            } catch (RuntimeException | IOException error) {
                Files.deleteIfExists(merged);
                if (error instanceof IllegalArgumentException) {
                    fileStorageUtil.deleteTree(directory);
                }
                throw error;
            }
        });
    }

    public Map<String, Object> checkFileExist(String identifier, String purpose, int userId) throws IOException {
        requireUserId(userId);
        String normalizedPurpose = fileValidator.normalizePurpose(purpose);
        return withUserLock(userId, () -> {
            long now = System.currentTimeMillis();
            cleanupUserExpiredNoLock(userId, now);
            Map<String, Object> result = new HashMap<>();
            UploadedFileInfo staged = findStagedNoLock(userId, normalizedPurpose, identifier, now);
            if (staged != null) {
                result.put("complete", true);
                result.put("file", staged);
                result.put("uploadedChunks", Collections.emptyList());
                return result;
            }

            Path directory = resolve(chunkDirectoryName(userId, normalizedPurpose, identifier));
            List<Integer> uploadedChunks = new ArrayList<>();
            Path metadata = directory.resolve(SESSION_METADATA);
            if (Files.isRegularFile(metadata)) {
                try (Stream<Path> paths = Files.list(directory)) {
                    uploadedChunks = paths
                            .filter(path -> path.getFileName().toString().matches("\\d+\\.part"))
                            .map(path -> path.getFileName().toString().replace(".part", ""))
                            .map(Integer::valueOf)
                            .sorted()
                            .collect(Collectors.toList());
                }
            }
            result.put("complete", false);
            result.put("uploadedChunks", uploadedChunks);
            return result;
        });
    }

    public StagedFile loadStagedFile(int userId, String purpose, String token) throws IOException {
        requireUserId(userId);
        String normalizedPurpose = fileValidator.normalizePurpose(purpose);
        requireToken(token);
        return withUserLock(userId, () -> {
            StagedFile staged = loadStagedByTokenNoLock(userId, normalizedPurpose, token,
                    System.currentTimeMillis(), true);
            if (staged == null) {
                throw new IllegalArgumentException("上传文件凭证不存在或已过期");
            }
            return staged;
        });
    }

    public void consumeStagedFile(StagedFile staged) throws IOException {
        if (staged == null) {
            return;
        }
        withUserLock(staged.userId, () -> {
            Files.deleteIfExists(staged.metadata);
            Files.deleteIfExists(staged.index);
            return null;
        });
    }

    public void cancelUpload(int userId, String purpose, String identifier, String token) throws IOException {
        requireUserId(userId);
        String normalizedPurpose = fileValidator.normalizePurpose(purpose);
        if ((identifier == null || identifier.trim().isEmpty()) && (token == null || token.trim().isEmpty())) {
            throw new IllegalArgumentException("缺少需要取消的上传标识");
        }
        withUserLock(userId, () -> {
            if (identifier != null && !identifier.trim().isEmpty()) {
                fileStorageUtil.deleteTree(resolve(chunkDirectoryName(userId, normalizedPurpose, identifier)));
            }
            if (token != null && !token.trim().isEmpty()) {
                requireToken(token);
                StagedFile staged = loadStagedByTokenNoLock(
                        userId, normalizedPurpose, token, System.currentTimeMillis(), false);
                if (staged != null) {
                    deleteStagedNoLock(staged);
                } else {
                    deleteTokenArtifactsNoLock(userId, normalizedPurpose, token);
                }
            }
            return null;
        });
    }

    @Scheduled(fixedDelayString = "${material.upload.cleanup-interval-ms:3600000}")
    public void cleanupExpiredUploads() {
        long now = System.currentTimeMillis();
        Set<Integer> userIds = new HashSet<>();
        collectUserIds(resolve(CHUNK_ROOT), userIds);
        collectUserIds(resolve(STAGING_ROOT), userIds);
        for (Integer userId : userIds) {
            try {
                withUserLock(userId, () -> {
                    cleanupUserExpiredNoLock(userId, now);
                    return null;
                });
            } catch (RuntimeException | IOException error) {
                log.warn("清理用户 {} 的过期上传失败: {}", userId, error.getMessage());
            }
        }
    }

    private void validateChunkGeometry(MultipartFile chunk, int chunkNumber, int totalChunks, long expectedSize) {
        if (chunk == null || chunk.isEmpty() || chunk.getSize() > MaterialFileValidator.MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("分片大小不合法");
        }
        long requiredChunks = (expectedSize + CHUNK_SIZE - 1L) / CHUNK_SIZE;
        if (totalChunks != requiredChunks) {
            throw new IllegalArgumentException("分片总数与文件大小不匹配");
        }
        long offset = (chunkNumber - 1L) * CHUNK_SIZE;
        long expectedChunkSize = Math.min(CHUNK_SIZE, expectedSize - offset);
        if (expectedChunkSize <= 0 || chunk.getSize() != expectedChunkSize) {
            throw new IllegalArgumentException("第 " + chunkNumber + " 个分片大小与声明不匹配");
        }
    }

    private void verifyAllParts(Path directory, UploadSession session) throws IOException {
        long totalSize = 0L;
        for (int chunkNumber = 1; chunkNumber <= session.totalChunks; chunkNumber++) {
            Path part = directory.resolve(chunkNumber + ".part");
            if (!Files.isRegularFile(part)) {
                throw new IllegalStateException("缺少第 " + chunkNumber + " 个分片");
            }
            long offset = (chunkNumber - 1L) * CHUNK_SIZE;
            long expectedPartSize = Math.min(CHUNK_SIZE, session.expectedSize - offset);
            long actualPartSize = Files.size(part);
            if (actualPartSize != expectedPartSize) {
                throw new IllegalArgumentException("第 " + chunkNumber + " 个分片大小校验失败");
            }
            totalSize += actualPartSize;
            if (totalSize > session.expectedSize) {
                throw new IllegalArgumentException("分片累计大小超过声明的文件大小");
            }
        }
        if (totalSize != session.expectedSize) {
            throw new IllegalArgumentException("分片总大小校验失败");
        }
    }

    private long partBytes(Path directory, Path excludedTarget) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0L;
        }
        long total = 0L;
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> parts = paths
                    .filter(path -> path.getFileName().toString().matches("\\d+\\.part"))
                    .filter(path -> !path.equals(excludedTarget))
                    .collect(Collectors.toList());
            for (Path part : parts) {
                total += Files.size(part);
            }
        }
        return total;
    }

    private int countActiveSessionsNoLock(int userId) throws IOException {
        Path userRoot = resolve(CHUNK_ROOT + "/" + userId);
        if (!Files.isDirectory(userRoot)) {
            return 0;
        }
        try (Stream<Path> paths = Files.walk(userRoot)) {
            return (int) paths.filter(path -> SESSION_METADATA.equals(path.getFileName().toString())).count();
        }
    }

    private long userTemporaryUsageNoLock(int userId) throws IOException {
        return directorySize(resolve(CHUNK_ROOT + "/" + userId))
                + directorySize(resolve(STAGING_ROOT + "/" + userId));
    }

    private long directorySize(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0L;
        }
        long total = 0L;
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> files = paths.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path file : files) {
                total += Files.size(file);
            }
        }
        return total;
    }

    private UploadedFileInfo findStagedNoLock(int userId, String purpose, String checksum, long now)
            throws IOException {
        Path directory = resolve(stagingDirectoryName(userId, purpose));
        Path index = indexPath(directory, checksum);
        if (!Files.isRegularFile(index)) {
            return null;
        }
        String token = new String(Files.readAllBytes(index), java.nio.charset.StandardCharsets.US_ASCII).trim();
        try {
            requireToken(token);
        } catch (IllegalArgumentException invalidIndex) {
            Files.deleteIfExists(index);
            return null;
        }
        StagedFile staged = loadStagedByTokenNoLock(userId, purpose, token, now, false);
        if (staged == null || !checksum.equalsIgnoreCase(staged.info.getChecksum())) {
            Files.deleteIfExists(index);
            return null;
        }
        return staged.info;
    }

    private StagedFile loadStagedByTokenNoLock(int userId, String purpose, String token, long now,
                                                boolean touch) throws IOException {
        Path directory = resolve(stagingDirectoryName(userId, purpose));
        Path metadata = directory.resolve(token + ".properties");
        if (!Files.isRegularFile(metadata)) {
            return null;
        }
        StagedMetadata stagedMetadata = readStagedMetadata(metadata);
        if (!token.equals(stagedMetadata.info.getToken())
                || !purpose.equals(stagedMetadata.info.getPurpose())
                || now - stagedMetadata.lastActivity > stagingTtlMillis) {
            StagedFile stale = stagedFile(userId, purpose, directory, metadata, stagedMetadata.info);
            deleteStagedNoLock(stale);
            return null;
        }
        Path file = directory.resolve(token + "." + stagedMetadata.info.getExtension()).normalize();
        if (!Files.isRegularFile(file)) {
            Files.deleteIfExists(metadata);
            Files.deleteIfExists(indexPath(directory, stagedMetadata.info.getChecksum()));
            return null;
        }
        long currentSize = Files.size(file);
        if (currentSize != stagedMetadata.info.getSize()) {
            throw new IllegalArgumentException("暂存文件大小已发生变化");
        }
        long currentLastModified = Files.getLastModifiedTime(file).toMillis();
        boolean fileStateChanged = currentLastModified != stagedMetadata.validatedLastModified;
        if (fileStateChanged) {
            UploadedFileInfo verified = fileValidator.validate(
                    file, stagedMetadata.info.getOriginalName(), purpose, stagedMetadata.info.getSize());
            if (!verified.getChecksum().equalsIgnoreCase(stagedMetadata.info.getChecksum())) {
                throw new IllegalArgumentException("暂存文件校验失败");
            }
            stagedMetadata.validatedLastModified = currentLastModified;
        }
        if (touch || fileStateChanged) {
            stagedMetadata.lastActivity = now;
            writeStagedMetadata(metadata, stagedMetadata.info, stagedMetadata.createdAt, now,
                    stagedMetadata.validatedLastModified);
        }
        return new StagedFile(userId, purpose, file, metadata,
                indexPath(directory, stagedMetadata.info.getChecksum()), stagedMetadata.info);
    }

    private StagedFile stagedFile(int userId, String purpose, Path directory, Path metadata,
                                  UploadedFileInfo info) {
        Path file = directory.resolve(info.getToken() + "." + info.getExtension()).normalize();
        return new StagedFile(userId, purpose, file, metadata,
                indexPath(directory, info.getChecksum()), info);
    }

    private void deleteStagedNoLock(StagedFile staged) throws IOException {
        Files.deleteIfExists(staged.path);
        Files.deleteIfExists(staged.metadata);
        Files.deleteIfExists(staged.index);
    }

    private void deleteTokenArtifactsNoLock(int userId, String purpose, String token) throws IOException {
        Path directory = resolve(stagingDirectoryName(userId, purpose));
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> matches = Files.newDirectoryStream(directory, token + ".*")) {
            for (Path match : matches) {
                if (Files.isRegularFile(match)) {
                    Files.deleteIfExists(match);
                }
            }
        }
    }

    private void cleanupUserExpiredNoLock(int userId, long now) throws IOException {
        Path chunkUserRoot = resolve(CHUNK_ROOT + "/" + userId);
        if (Files.isDirectory(chunkUserRoot)) {
            try (Stream<Path> paths = Files.walk(chunkUserRoot, 3)) {
                List<Path> sessionDirectories = paths
                        .filter(Files::isDirectory)
                        .filter(path -> path.getParent() != null && path.getParent().getParent() != null)
                        .filter(path -> path.getParent().getParent().equals(chunkUserRoot))
                        .collect(Collectors.toList());
                for (Path directory : sessionDirectories) {
                    Path metadata = directory.resolve(SESSION_METADATA);
                    long lastActivity = Files.isRegularFile(metadata)
                            ? readSession(metadata).lastActivity
                            : Files.getLastModifiedTime(directory).toMillis();
                    if (now - lastActivity > sessionTtlMillis) {
                        fileStorageUtil.deleteTree(directory);
                    }
                }
            }
        }

        Path stagingUserRoot = resolve(STAGING_ROOT + "/" + userId);
        if (!Files.isDirectory(stagingUserRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(stagingUserRoot, 2)) {
            List<Path> metadataFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .collect(Collectors.toList());
            for (Path metadata : metadataFiles) {
                StagedMetadata staged = readStagedMetadata(metadata);
                if (now - staged.lastActivity > stagingTtlMillis) {
                    deleteStagedNoLock(stagedFile(
                            userId, staged.info.getPurpose(), metadata.getParent(), metadata, staged.info));
                }
            }
        }
        cleanupOrphanStagedFiles(stagingUserRoot, now);
    }

    private void cleanupOrphanStagedFiles(Path stagingUserRoot, long now) throws IOException {
        try (Stream<Path> paths = Files.walk(stagingUserRoot, 3)) {
            List<Path> files = paths.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.endsWith(".properties")) {
                    continue;
                }
                if (name.endsWith(".token")) {
                    String indexedToken = new String(
                            Files.readAllBytes(file), java.nio.charset.StandardCharsets.US_ASCII).trim();
                    Path purposeDirectory = file.getParent().getParent();
                    Path indexedMetadata = purposeDirectory.resolve(indexedToken + ".properties");
                    if (!Files.isRegularFile(indexedMetadata)
                            && now - Files.getLastModifiedTime(file).toMillis() > stagingTtlMillis) {
                        Files.deleteIfExists(file);
                    }
                    continue;
                }
                int dot = name.indexOf('.');
                if (dot <= 0) {
                    continue;
                }
                Path metadata = file.resolveSibling(name.substring(0, dot) + ".properties");
                if (!Files.exists(metadata)
                        && now - Files.getLastModifiedTime(file).toMillis() > stagingTtlMillis) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private void collectUserIds(Path root, Set<Integer> userIds) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(root)) {
            for (Path directory : directories) {
                if (!Files.isDirectory(directory)) {
                    continue;
                }
                try {
                    int userId = Integer.parseInt(directory.getFileName().toString());
                    if (userId > 0) {
                        userIds.add(userId);
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("跳过无法识别的上传用户目录: {}", directory);
                }
            }
        } catch (IOException error) {
            log.warn("扫描上传用户目录失败 {}: {}", root, error.getMessage());
        }
    }

    private UploadSession readSession(Path metadata) throws IOException {
        Properties properties = loadProperties(metadata);
        return new UploadSession(
                Integer.parseInt(properties.getProperty("userId", "0")),
                properties.getProperty("purpose"),
                properties.getProperty("identifier"),
                properties.getProperty("filename"),
                Long.parseLong(properties.getProperty("expectedSize", "0")),
                Integer.parseInt(properties.getProperty("totalChunks", "0")),
                Long.parseLong(properties.getProperty("createdAt", "0")),
                Long.parseLong(properties.getProperty("lastActivity", "0"))
        );
    }

    private void writeSession(Path metadata, UploadSession session) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("userId", String.valueOf(session.userId));
        properties.setProperty("purpose", session.purpose);
        properties.setProperty("identifier", session.identifier);
        properties.setProperty("filename", session.filename);
        properties.setProperty("expectedSize", String.valueOf(session.expectedSize));
        properties.setProperty("totalChunks", String.valueOf(session.totalChunks));
        properties.setProperty("createdAt", String.valueOf(session.createdAt));
        properties.setProperty("lastActivity", String.valueOf(session.lastActivity));
        storePropertiesAtomically(metadata, properties, "Ganlu material upload session");
    }

    private StagedMetadata readStagedMetadata(Path metadata) throws IOException {
        Properties properties = loadProperties(metadata);
        UploadedFileInfo info = new UploadedFileInfo();
        info.setToken(properties.getProperty("token"));
        info.setOriginalName(properties.getProperty("originalName"));
        info.setExtension(properties.getProperty("extension"));
        info.setMimeType(properties.getProperty("mimeType"));
        info.setChecksum(properties.getProperty("checksum"));
        info.setSize(Long.parseLong(properties.getProperty("size", "0")));
        info.setPurpose(properties.getProperty("purpose"));
        long createdAt = Long.parseLong(properties.getProperty("createdAt", "0"));
        long lastActivity = Long.parseLong(properties.getProperty("lastActivity", String.valueOf(createdAt)));
        long validatedLastModified = Long.parseLong(properties.getProperty("validatedLastModified", "0"));
        return new StagedMetadata(info, createdAt, lastActivity, validatedLastModified);
    }

    private void writeStagedMetadata(Path metadata, UploadedFileInfo info, long createdAt, long lastActivity,
                                     long validatedLastModified)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty("token", info.getToken());
        properties.setProperty("originalName", info.getOriginalName());
        properties.setProperty("extension", info.getExtension());
        properties.setProperty("mimeType", info.getMimeType());
        properties.setProperty("checksum", info.getChecksum());
        properties.setProperty("size", String.valueOf(info.getSize()));
        properties.setProperty("purpose", info.getPurpose());
        properties.setProperty("createdAt", String.valueOf(createdAt));
        properties.setProperty("lastActivity", String.valueOf(lastActivity));
        properties.setProperty("validatedLastModified", String.valueOf(validatedLastModified));
        storePropertiesAtomically(metadata, properties, "Ganlu material staged upload");
    }

    private Properties loadProperties(Path metadata) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) {
            properties.load(input);
        } catch (NumberFormatException error) {
            throw new IOException("上传元数据损坏: " + metadata, error);
        }
        return properties;
    }

    private void writeIndex(Path index, String token) throws IOException {
        Files.createDirectories(index.getParent());
        Path temporary = index.resolveSibling(index.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.write(temporary, token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        moveAtomically(temporary, index);
    }

    private void storePropertiesAtomically(Path target, Properties properties, String comment) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp-" + UUID.randomUUID());
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, comment);
        }
        moveAtomically(temporary, target);
    }

    private void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path indexPath(Path stagingDirectory, String checksum) {
        return stagingDirectory.resolve(INDEX_DIRECTORY).resolve(checksum.toLowerCase() + ".token").normalize();
    }

    private String chunkDirectoryName(int userId, String purpose, String identifier) {
        return CHUNK_ROOT + "/" + userId + "/" + purpose.toLowerCase() + "/" + identifier.toLowerCase();
    }

    private String stagingDirectoryName(int userId, String purpose) {
        return STAGING_ROOT + "/" + userId + "/" + purpose.toLowerCase();
    }

    private Path createDirectory(String relativePath) {
        return fileStorageUtil.createDirectory(relativePath);
    }

    private Path resolve(String relativePath) {
        Path root = fileStorageUtil.getUploadRoot();
        Path path = root.resolve(relativePath).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new SecurityException("非法上传路径");
        }
        return path;
    }

    private <T> T withUserLock(int userId, IoOperation<T> operation) throws IOException {
        Object monitor = userLocks.computeIfAbsent(userId, ignored -> new Object());
        synchronized (monitor) {
            Path userDirectory = createDirectory(CHUNK_ROOT + "/" + userId);
            Path lockPath = userDirectory.resolve(".user.lock");
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                return operation.run();
            }
        }
    }

    private void requireUserId(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("上传用户不合法");
        }
    }

    private void requireToken(String token) {
        try {
            UUID.fromString(token);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("上传文件凭证不合法");
        }
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws IOException;
    }

    private static final class UploadSession {
        private final int userId;
        private final String purpose;
        private final String identifier;
        private final String filename;
        private final long expectedSize;
        private final int totalChunks;
        private final long createdAt;
        private long lastActivity;

        private UploadSession(int userId, String purpose, String identifier, String filename,
                              long expectedSize, int totalChunks, long createdAt, long lastActivity) {
            this.userId = userId;
            this.purpose = purpose;
            this.identifier = identifier;
            this.filename = filename;
            this.expectedSize = expectedSize;
            this.totalChunks = totalChunks;
            this.createdAt = createdAt;
            this.lastActivity = lastActivity;
        }

        private void requireMatches(int actualUserId, String actualPurpose, String actualIdentifier,
                                    String actualFilename, long actualSize, int actualChunks) {
            if (userId != actualUserId
                    || !purpose.equals(actualPurpose)
                    || !identifier.equalsIgnoreCase(actualIdentifier)
                    || !filename.equals(actualFilename)
                    || expectedSize != actualSize
                    || totalChunks != actualChunks) {
                throw new IllegalArgumentException("上传参数与已建立的会话不一致");
            }
        }
    }

    private static final class StagedMetadata {
        private final UploadedFileInfo info;
        private final long createdAt;
        private long lastActivity;
        private long validatedLastModified;

        private StagedMetadata(UploadedFileInfo info, long createdAt, long lastActivity,
                               long validatedLastModified) {
            this.info = info;
            this.createdAt = createdAt;
            this.lastActivity = lastActivity;
            this.validatedLastModified = validatedLastModified;
        }
    }

    public static final class StagedFile {
        private final int userId;
        private final String purpose;
        private final Path path;
        private final Path metadata;
        private final Path index;
        private final UploadedFileInfo info;

        private StagedFile(int userId, String purpose, Path path, Path metadata,
                           Path index, UploadedFileInfo info) {
            this.userId = userId;
            this.purpose = purpose;
            this.path = path;
            this.metadata = metadata;
            this.index = index;
            this.info = info;
        }

        public Path getPath() {
            return path;
        }

        public UploadedFileInfo getInfo() {
            return info;
        }
    }
}
