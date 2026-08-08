package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.mappers.TeamMediaQuotaMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TeamMediaCapacityService {
    private final FileStorageUtil fileStorageUtil;
    private final TeamMediaQuotaMapper quotaMapper;
    private final Path multipartTempDirectory;
    private final long uploadReserveBytes;
    private final long multipartReserveBytes;
    private final int maxConcurrentUploads;
    private final int maxRequestsPerUserPerMinute;
    private final int reservationTtlMinutes;
    private final ThreadLocal<UploadAdmission> currentAdmission = new ThreadLocal<>();

    public TeamMediaCapacityService(
            FileStorageUtil fileStorageUtil,
            TeamMediaQuotaMapper quotaMapper,
            @Value("${spring.servlet.multipart.location:}") String multipartLocation,
            @Value("${team.media.upload-min-free-disk-mb:1024}") long uploadReserveMb,
            @Value("${team.media.multipart-min-free-disk-mb:1024}") long multipartReserveMb,
            @Value("${team.media.max-concurrent-uploads:4}") int maxConcurrentUploads,
            @Value("${team.media.max-requests-per-user-per-minute:12}") int maxRequestsPerUserPerMinute,
            @Value("${team.media.upload-reservation-ttl-minutes:120}") int reservationTtlMinutes) {
        this.fileStorageUtil = fileStorageUtil;
        this.quotaMapper = quotaMapper;
        String location = multipartLocation == null ? "" : multipartLocation.trim();
        this.multipartTempDirectory = Paths.get(location.isEmpty()
                ? System.getProperty("java.io.tmpdir") : location).toAbsolutePath().normalize();
        this.uploadReserveBytes = Math.max(0L, uploadReserveMb) * 1024L * 1024L;
        this.multipartReserveBytes = Math.max(0L, multipartReserveMb) * 1024L * 1024L;
        this.maxConcurrentUploads = Math.max(1, maxConcurrentUploads);
        this.maxRequestsPerUserPerMinute = Math.max(1, maxRequestsPerUserPerMinute);
        this.reservationTtlMinutes = Math.max(5, reservationTtlMinutes);
    }

    /**
     * 在 Multipart 解析前调用。全局协调行把所有实例的“检查 + 预留”串成原子操作。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UploadAdmission reserveAdmission(int ownerUserId, long incomingBytes) {
        if (ownerUserId <= 0) throw new IllegalArgumentException("附件上传用户不正确");
        if (incomingBytes <= 0) throw new IllegalArgumentException("上传文件大小不正确");
        ensureDirectory(multipartTempDirectory);
        ensureDirectory(fileStorageUtil.getUploadRoot());

        quotaMapper.ensureGlobalQuotaRow();
        if (quotaMapper.lockGlobalQuotaRow() == null) {
            throw new IllegalStateException("无法锁定附件上传协调账本");
        }
        Instant now = Instant.now();
        quotaMapper.cleanupUploadReservations(
                Timestamp.from(now.minus(Math.max(120, reservationTtlMinutes * 2L), ChronoUnit.MINUTES)));
        int activeCount = zeroIfNull(quotaMapper.countActiveUploadReservations());
        long activeBytes = zeroIfNull(quotaMapper.sumActiveUploadReservationBytes());
        if (activeCount >= maxConcurrentUploads) {
            throw new UploadAdmissionException(429, "同时上传的附件过多，请稍后重试");
        }
        int recentRequests = zeroIfNull(quotaMapper.countRecentUploadAttempts(
                ownerUserId, Timestamp.from(now.minus(1, ChronoUnit.MINUTES))));
        if (recentRequests >= maxRequestsPerUserPerMinute) {
            throw new UploadAdmissionException(429, "上传过于频繁，请一分钟后重试");
        }
        ensureDeviceCapacity(activeBytes, incomingBytes);

        String reservationId = UUID.randomUUID().toString();
        Timestamp expiresAt = Timestamp.from(now.plus(reservationTtlMinutes, ChronoUnit.MINUTES));
        if (quotaMapper.insertUploadReservation(
                reservationId, ownerUserId, incomingBytes, expiresAt) != 1) {
            throw new IllegalStateException("登记附件在途容量失败");
        }
        return new UploadAdmission(reservationId, ownerUserId, incomingBytes);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseAdmission(String reservationId) {
        if (reservationId != null && !reservationId.trim().isEmpty()) {
            quotaMapper.releaseUploadReservation(reservationId);
        }
    }

    public void bindToCurrentRequest(UploadAdmission admission) {
        if (admission == null) throw new IllegalArgumentException("上传预留不能为空");
        currentAdmission.set(admission);
    }

    public void clearCurrentRequest() {
        currentAdmission.remove();
    }

    /**
     * Multipart 已完成后、复制到正式目录前的二次检查。当前请求的预留不重复扣减，
     * 其他实例/请求的在途字节仍计入安全余量。
     */
    public void ensureFormalCapacity(long incomingBytes) {
        if (incomingBytes <= 0) throw new IllegalArgumentException("上传文件大小不正确");
        long activeBytes = zeroIfNull(quotaMapper.sumActiveUploadReservationBytes());
        UploadAdmission own = currentAdmission.get();
        long ownBytes = own == null ? 0L : own.getReservedBytes();
        long otherActiveBytes = Math.max(0L, activeBytes - ownBytes);
        requireFreeSpace(fileStorageUtil.getUploadRoot(), uploadReserveBytes,
                safeAdd(otherActiveBytes, incomingBytes), "正式附件目录");
    }

    public Path getMultipartTempDirectory() {
        return multipartTempDirectory;
    }

    private void ensureDeviceCapacity(long activeBytes, long incomingBytes) {
        Map<String, DeviceRequirement> devices = new LinkedHashMap<>();
        addDevice(devices, fileStorageUtil.getUploadRoot(), uploadReserveBytes, "正式附件目录");
        addDevice(devices, multipartTempDirectory, multipartReserveBytes, "Multipart 临时目录");
        long pendingBytes = safeAdd(activeBytes, incomingBytes);
        for (DeviceRequirement device : devices.values()) {
            requireFreeSpace(device.path, device.reserveBytes, pendingBytes, device.label);
        }
    }

    private void addDevice(
            Map<String, DeviceRequirement> devices, Path path, long reserveBytes, String label) {
        try {
            FileStore store = Files.getFileStore(path.toAbsolutePath().normalize());
            String key = store.name() + "|" + store.type();
            DeviceRequirement existing = devices.get(key);
            if (existing == null || reserveBytes > existing.reserveBytes) {
                devices.put(key, new DeviceRequirement(path, reserveBytes,
                        existing == null ? label : existing.label + "/" + label));
            }
        } catch (Exception error) {
            throw new FileStorageUtil.StorageException("无法识别上传目录所在磁盘: " + path, error);
        }
    }

    private void requireFreeSpace(Path directory, long reserveBytes, long pendingBytes, String label) {
        long usable = fileStorageUtil.getUsableSpace(directory);
        long required = safeAdd(reserveBytes, pendingBytes);
        if (usable < required) {
            throw new UploadAdmissionException(507, label + "剩余空间不足，已拒绝本次上传");
        }
    }

    private void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (Exception error) {
            throw new FileStorageUtil.StorageException("无法初始化上传目录: " + directory, error);
        }
    }

    private long safeAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new UploadAdmissionException(507, "上传容量计算溢出，已拒绝本次上传");
        }
        return left + right;
    }

    private int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    private long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    private static final class DeviceRequirement {
        private final Path path;
        private final long reserveBytes;
        private final String label;

        private DeviceRequirement(Path path, long reserveBytes, String label) {
            this.path = path;
            this.reserveBytes = reserveBytes;
            this.label = label;
        }
    }

    public static final class UploadAdmission {
        private final String reservationId;
        private final int ownerUserId;
        private final long reservedBytes;

        public UploadAdmission(String reservationId, int ownerUserId, long reservedBytes) {
            this.reservationId = reservationId;
            this.ownerUserId = ownerUserId;
            this.reservedBytes = reservedBytes;
        }

        public String getReservationId() { return reservationId; }
        public int getOwnerUserId() { return ownerUserId; }
        public long getReservedBytes() { return reservedBytes; }
    }

    public static class UploadAdmissionException extends IllegalStateException {
        private final int httpStatus;

        public UploadAdmissionException(int httpStatus, String message) {
            super(message);
            this.httpStatus = httpStatus;
        }

        public int getHttpStatus() {
            return httpStatus;
        }
    }
}
