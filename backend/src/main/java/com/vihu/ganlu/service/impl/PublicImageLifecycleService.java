package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.PublicImageUploadInfo;
import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.PublicImageValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class PublicImageLifecycleService {
    private static final String STAGING_ROOT = "staging/public-images";
    private static final String PUBLIC_ROOT = "images";
    private static final String PRIVATE_ROOT = "images_pending";
    private static final List<String> EXTENSIONS = Arrays.asList("jpg", "png", "webp");

    private final FileStorageUtil fileStorageUtil;
    private final PublicImageValidator validator;
    private final PublicImageQuotaMapper quotaMapper;
    private final PublicImageAssetDeletionService assetDeletionService;
    private final long stagingTtlMillis;
    private final long userQuotaBytes;
    private final int maxStagedFilesPerUser;
    private final long permanentQuotaBytes;
    private final int maxPermanentFilesPerUser;
    private final long minFreeDiskBytes;
    private final ConcurrentHashMap<Integer, Object> userLocks = new ConcurrentHashMap<>();

    public PublicImageLifecycleService(
            FileStorageUtil fileStorageUtil,
            PublicImageValidator validator,
            PublicImageQuotaMapper quotaMapper,
            PublicImageAssetDeletionService assetDeletionService,
            @Value("${team.public-image.staging-ttl-hours:24}") long stagingTtlHours,
            @Value("${team.public-image.user-temp-quota-mb:50}") long userQuotaMegabytes,
            @Value("${team.public-image.max-staged-files-per-user:10}") int maxStagedFilesPerUser,
            @Value("${team.public-image.user-permanent-quota-mb:500}") long permanentQuotaMegabytes,
            @Value("${team.public-image.max-permanent-files-per-user:100}") int maxPermanentFilesPerUser,
            @Value("${team.public-image.min-free-disk-mb:1024}") long minFreeDiskMegabytes) {
        this.fileStorageUtil = fileStorageUtil;
        this.validator = validator;
        this.quotaMapper = quotaMapper;
        this.assetDeletionService = assetDeletionService;
        this.stagingTtlMillis = Duration.ofHours(Math.max(1L, stagingTtlHours)).toMillis();
        this.userQuotaBytes = Math.max(5L, userQuotaMegabytes) * 1024L * 1024L;
        this.maxStagedFilesPerUser = Math.max(1, maxStagedFilesPerUser);
        this.permanentQuotaBytes = Math.max(5L, permanentQuotaMegabytes) * 1024L * 1024L;
        this.maxPermanentFilesPerUser = Math.max(1, maxPermanentFilesPerUser);
        this.minFreeDiskBytes = Math.max(0L, minFreeDiskMegabytes) * 1024L * 1024L;
    }

    public PublicImageUploadInfo stage(MultipartFile file, int userId) {
        requireUserId(userId);
        PublicImageValidator.ValidatedImage validated = validator.validate(file);
        ensureDiskCapacity(file.getSize());
        synchronized (lockFor(userId)) {
            cleanupUserExpired(userId, System.currentTimeMillis());
            List<Path> stagedFiles = stagedFiles(userId);
            if (stagedFiles.size() >= maxStagedFilesPerUser) {
                throw new IllegalStateException("临时图片数量已达到上限，请先保存或取消已有上传");
            }
            long currentBytes = stagedFiles.stream().mapToLong(this::safeSize).sum();
            if (currentBytes + file.getSize() > userQuotaBytes) {
                throw new IllegalStateException("当前账号的临时图片空间已达到上限");
            }

            String relativePath = fileStorageUtil.storeFile(
                    file, stagingDirectory(userId), validated.getExtension());
            String filename = fileStorageUtil.loadFile(relativePath).getFileName().toString();
            String token = filename.substring(0, filename.lastIndexOf('.'));
            return new PublicImageUploadInfo(
                    token,
                    FileStorageUtil.safeLeafName(file.getOriginalFilename()),
                    validated.getExtension(),
                    validated.getContentType(),
                    file.getSize());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String promote(int userId, String token) {
        return promote(userId, token, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String promotePrivate(int userId, String token) {
        return promote(userId, token, false);
    }

    private String promote(int userId, String token, boolean makePublic) {
        requireUserId(userId);
        requireToken(token);
        synchronized (lockFor(userId)) {
            Path staged = findStagedFile(userId, token, true);
            String extension = FileStorageUtil.extensionOf(staged.getFileName().toString());
            long fileSize = safeSize(staged);
            reservePermanentQuota(userId, fileSize);
            String publicPath = fileStorageUtil.moveInto(
                    staged, managedRoot(makePublic) + "/" + userId, extension);
            registerRollbackCleanup(publicPath);
            PublicImageAssetEntity asset = new PublicImageAssetEntity();
            asset.setRelativePath(publicPath);
            asset.setOwnerUserId(userId);
            asset.setFileSize(fileSize);
            if (quotaMapper.insertAsset(asset) != 1) {
                throw new IllegalStateException("登记公共图片失败");
            }
            return publicPath;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String moveManagedImage(String relativePath, boolean makePublic) {
        String normalized = normalizeManagedImagePath(relativePath);
        if (normalized == null) {
            throw new IllegalArgumentException("图片路径不属于统一生命周期管理");
        }
        PublicImageAssetEntity asset = quotaMapper.findAsset(normalized);
        if (asset == null || asset.getAssetId() == null) {
            throw new IllegalStateException("图片资源账本缺失，禁止改变公开状态");
        }
        String targetRoot = managedRoot(makePublic);
        if (normalized.startsWith(targetRoot + "/")) return normalized;

        String filename = fileStorageUtil.loadFile(normalized).getFileName().toString();
        String targetPath = targetRoot + "/" + asset.getOwnerUserId() + "/" + filename;
        fileStorageUtil.moveFile(normalized, targetPath);
        registerRollbackMove(targetPath, normalized);
        if (quotaMapper.updateAssetPath(asset.getAssetId(), targetPath) != 1) {
            throw new IllegalStateException("更新图片资源位置失败");
        }
        return targetPath;
    }

    public void cancel(int userId, String token) {
        requireUserId(userId);
        requireToken(token);
        synchronized (lockFor(userId)) {
            Path staged = findStagedFile(userId, token, false);
            if (staged != null) {
                safeDelete(fileStorageUtil.toRelativePath(staged));
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deletePublicImageAfterCommit(String relativePath) {
        String normalized = normalizeManagedImagePath(relativePath);
        if (normalized == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        assetDeletionService.deletePhysicalFileThenReleaseQuota(normalized);
                    } catch (RuntimeException error) {
                        log.error("公共图片删除失败，配额保持占用以便重试: {}", normalized, error);
                    }
                }
            });
        } else {
            assetDeletionService.deletePhysicalFileThenReleaseQuota(normalized);
        }
    }

    @Scheduled(fixedDelayString = "${team.public-image.cleanup-interval-ms:3600000}")
    public void cleanupExpiredUploads() {
        Path root = fileStorageUtil.loadFile(STAGING_ROOT);
        if (!Files.isDirectory(root)) return;
        long now = System.currentTimeMillis();
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).collect(Collectors.toList())) {
                try {
                    int userId = Integer.parseInt(directory.getFileName().toString());
                    synchronized (lockFor(userId)) {
                        cleanupUserExpired(userId, now);
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("跳过未知的公共图片暂存目录: {}", directory);
                }
            }
        } catch (IOException error) {
            log.warn("扫描过期公共图片失败", error);
        }
    }

    private void cleanupUserExpired(int userId, long now) {
        for (Path staged : stagedFiles(userId)) {
            try {
                if (now - Files.getLastModifiedTime(staged).toMillis() > stagingTtlMillis) {
                    Files.deleteIfExists(staged);
                }
            } catch (IOException error) {
                log.warn("清理过期公共图片失败: {}", staged, error);
            }
        }
    }

    private List<Path> stagedFiles(int userId) {
        Path directory = fileStorageUtil.loadFile(stagingDirectory(userId));
        if (!Files.isDirectory(directory)) return java.util.Collections.emptyList();
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> EXTENSIONS.contains(FileStorageUtil.extensionOf(path.getFileName().toString())))
                    .collect(Collectors.toList());
        } catch (IOException error) {
            throw new FileStorageUtil.StorageException("读取公共图片暂存目录失败", error);
        }
    }

    private Path findStagedFile(int userId, String token, boolean required) {
        for (String extension : EXTENSIONS) {
            Path candidate = fileStorageUtil.loadFile(stagingDirectory(userId) + "/" + token + "." + extension);
            if (Files.isRegularFile(candidate)) {
                try {
                    if (System.currentTimeMillis() - Files.getLastModifiedTime(candidate).toMillis()
                            > stagingTtlMillis) {
                        Files.deleteIfExists(candidate);
                        break;
                    }
                } catch (IOException error) {
                    throw new FileStorageUtil.StorageException("读取暂存图片失败", error);
                }
                return candidate;
            }
        }
        if (required) throw new IllegalArgumentException("图片上传凭证不存在或已过期");
        return null;
    }

    private void registerRollbackCleanup(String publicPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    safeDelete(publicPath);
                }
            }
        });
    }

    private void registerRollbackMove(String currentPath, String rollbackPath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        fileStorageUtil.moveFile(currentPath, rollbackPath);
                    } catch (RuntimeException error) {
                        log.error("回滚图片移动失败 {} -> {}", currentPath, rollbackPath, error);
                    }
                }
            }
        });
    }

    private String normalizeManagedImagePath(String relativePath) {
        if (relativePath == null) return null;
        String normalized = relativePath.trim().replace('\\', '/').replaceFirst("^/+", "");
        return normalized.matches("^(?:images|images_pending)/(?:[1-9][0-9]*/)?[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")
                ? normalized : null;
    }

    private String managedRoot(boolean makePublic) {
        return makePublic ? PUBLIC_ROOT : PRIVATE_ROOT;
    }

    private void reservePermanentQuota(int userId, long fileSize) {
        quotaMapper.ensureQuotaRow(userId);
        int reserved = quotaMapper.reservePermanentQuota(
                userId, fileSize, maxPermanentFilesPerUser, permanentQuotaBytes);
        if (reserved != 1) {
            throw new IllegalStateException("当前账号的正式图片数量或容量已达到上限，请先删除不再使用的图片");
        }
    }

    private void ensureDiskCapacity(long incomingBytes) {
        if (minFreeDiskBytes <= 0) return;
        long usableBytes = fileStorageUtil.getUsableSpace();
        if (usableBytes < incomingBytes || usableBytes - incomingBytes < minFreeDiskBytes) {
            throw new IllegalStateException("服务器图片存储空间不足，请联系管理员清理后再上传");
        }
    }

    private void safeDelete(String relativePath) {
        try {
            fileStorageUtil.deleteFile(relativePath);
        } catch (RuntimeException error) {
            log.warn("清理公共图片失败 {}: {}", relativePath, error.getMessage());
        }
    }

    private long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException error) {
            throw new FileStorageUtil.StorageException("读取暂存图片大小失败", error);
        }
    }

    private Object lockFor(int userId) {
        return userLocks.computeIfAbsent(userId, ignored -> new Object());
    }

    private String stagingDirectory(int userId) {
        return STAGING_ROOT + "/" + userId;
    }

    private void requireUserId(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("上传用户不正确");
    }

    private void requireToken(String token) {
        try {
            if (!UUID.fromString(token).toString().equalsIgnoreCase(token)) throw new IllegalArgumentException();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("图片上传凭证不合法");
        }
    }
}
