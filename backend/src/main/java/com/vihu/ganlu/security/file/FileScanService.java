package com.vihu.ganlu.security.file;

import com.vihu.ganlu.entitys.AuditEventEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.mappers.FileSecurityScanMapper;
import com.vihu.ganlu.service.AuditEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous gate around an asynchronous-capable malware scanner.
 * Every non-clean verdict remains PENDING for publication purposes.  A scan
 * record is written before and after the attempt when the SQL patch is
 * present; persistence failures never turn a file into CLEAN.
 */
@Slf4j
@Service
public class FileScanService {
    private final MalwareScanner scanner;
    private final FileSecurityScanMapper scanMapper;
    private final AuditEventService auditEventService;
    private final long timeoutMillis;
    private final ExecutorService executor;
    private final Map<String, FileScanResult> latest = new ConcurrentHashMap<>();

    @Autowired
    public FileScanService(
            MalwareScanner scanner,
            FileSecurityScanMapper scanMapper,
            AuditEventService auditEventService,
            @Value("${file.security.scan.timeout-ms:5000}") long timeoutMillis) {
        this(scanner, scanMapper, auditEventService, timeoutMillis, newExecutor());
    }

    /** Constructor used by focused tests; no database or Spring context required. */
    public FileScanService(MalwareScanner scanner, long timeoutMillis) {
        this(scanner, null, null, timeoutMillis, newExecutor());
    }

    FileScanService(MalwareScanner scanner, FileSecurityScanMapper scanMapper,
                    AuditEventService auditEventService, long timeoutMillis,
                    ExecutorService executor) {
        this.scanner = scanner;
        this.scanMapper = scanMapper;
        this.auditEventService = auditEventService;
        this.timeoutMillis = Math.max(1L, timeoutMillis);
        this.executor = executor;
    }

    /**
     * Scan a file.  The returned status is CLEAN, INFECTED, or PENDING; the
     * diagnostic verdict records why PENDING was retained.
     */
    public FileScanResult scan(Path file, String scope, Integer ownerUserId) {
        String relativePath = safePath(file);
        String normalizedScope = normalizeScope(scope);
        String sha256 = digestOrNull(file);
        persist(relativePath, normalizedScope, ownerUserId, FileSecurityStatus.PENDING,
                MalwareScanner.ScanVerdict.UNAVAILABLE, sha256, "SCAN_PENDING");

        MalwareScanner.ScanVerdict verdict = MalwareScanner.ScanVerdict.UNAVAILABLE;
        String detail = "扫描服务不可用";
        if (scanner == null) {
            detail = "未配置扫描服务";
        } else if (file == null || !Files.isRegularFile(file)) {
            verdict = MalwareScanner.ScanVerdict.FAILED;
            detail = "隔离文件不存在";
        } else {
            Future<MalwareScanner.ScanVerdict> future = executor.submit(() -> scanner.scan(file));
            try {
                verdict = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                if (verdict == null) verdict = MalwareScanner.ScanVerdict.FAILED;
                detail = verdict.name();
            } catch (TimeoutException timeout) {
                future.cancel(true);
                verdict = MalwareScanner.ScanVerdict.TIMEOUT;
                detail = "扫描超时";
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                verdict = MalwareScanner.ScanVerdict.TIMEOUT;
                detail = "扫描被中断";
            } catch (Exception error) {
                verdict = MalwareScanner.ScanVerdict.FAILED;
                detail = error.getClass().getSimpleName();
            }
        }

        FileSecurityStatus publicationStatus = publicationStatus(verdict);
        boolean finalRecordPersisted = persist(relativePath, normalizedScope, ownerUserId,
                publicationStatus, verdict, sha256, detail);
        // A CLEAN result is not durable until its ledger row is recorded. If
        // the database/table is unavailable, retain PENDING and fail closed.
        if (publicationStatus == FileSecurityStatus.CLEAN && !finalRecordPersisted) {
            publicationStatus = FileSecurityStatus.PENDING;
            detail = "扫描结果无法持久化";
        }
        FileScanResult result = new FileScanResult(relativePath, publicationStatus,
                verdict, detail, sha256, new Date());
        latest.put(relativePath, result);
        audit(normalizedScope, relativePath, publicationStatus, verdict);
        return result;
    }

    /** Convenience overload for callers that do not have an owner identifier. */
    public FileScanResult scan(Path file, String scope) {
        return scan(file, scope, null);
    }

    /** Return the last known result; absence is always treated as PENDING. */
    public FileScanResult getLatest(Path file) {
        String key = safePath(file);
        FileScanResult result = latest.get(key);
        if (result != null) return result;
        if (scanMapper != null) {
            try {
                com.vihu.ganlu.entitys.FileSecurityScanEntity entity = scanMapper.findByPath(key);
                if (entity != null) {
                    result = new FileScanResult(key, parseStatus(entity.getScanStatus()),
                            parseVerdict(entity.getDiagnosticStatus()), entity.getDetail(),
                            entity.getSha256(), entity.getCompletedAt());
                    latest.put(key, result);
                    return result;
                }
            } catch (RuntimeException error) {
                log.warn("读取文件扫描记录失败，按 PENDING 处理: {}", key);
            }
        }
        return new FileScanResult(key, FileSecurityStatus.PENDING,
                MalwareScanner.ScanVerdict.UNAVAILABLE, "无扫描记录", null, new Date());
    }

    public boolean isClean(Path file) {
        return getLatest(file).isClean() && file != null && Files.isRegularFile(file);
    }

    public void requireClean(Path file) {
        FileScanResult result = getLatest(file);
        if (!result.isClean() || file == null || !Files.isRegularFile(file)) {
            throw new FileSecurityException("文件尚未通过安全扫描，禁止公开或下载", result);
        }
    }

    /** Keep a clean verdict attached to the controlled path after promotion. */
    public boolean moveRecord(String fromPath, String toPath) {
        if (fromPath == null || toPath == null) return false;
        String fromKey = normalizeKey(fromPath);
        String toKey = normalizeKey(toPath);
        FileScanResult result = latest.get(fromKey);
        if (result == null && scanMapper != null) {
            try {
                com.vihu.ganlu.entitys.FileSecurityScanEntity entity = scanMapper.findByPath(fromKey);
                if (entity != null) {
                    result = new FileScanResult(fromKey, parseStatus(entity.getScanStatus()),
                            parseVerdict(entity.getDiagnosticStatus()), entity.getDetail(),
                            entity.getSha256(), entity.getCompletedAt());
                }
            } catch (RuntimeException error) {
                log.warn("读取待移动文件扫描记录失败: {}", fromKey);
            }
        }
        if (result == null || !result.isClean()) return false;
        if (scanMapper != null) {
            try {
                if (scanMapper.updatePath(fromKey, toKey) != 1) return false;
            } catch (RuntimeException error) {
                log.warn("更新文件扫描路径失败，保留 fail-closed 状态: {}", fromKey);
                return false;
            }
        }
        latest.remove(fromKey);
        latest.put(toKey, new FileScanResult(toKey, result.getStatus(),
                result.getDiagnosticVerdict(), result.getDetail(), result.getSha256(),
                result.getCompletedAt()));
        return true;
    }

    private boolean persist(String path, String scope, Integer owner, FileSecurityStatus status,
                            MalwareScanner.ScanVerdict verdict, String sha256, String detail) {
        if (scanMapper == null || path == null) return true;
        try {
            com.vihu.ganlu.entitys.FileSecurityScanEntity entity = new com.vihu.ganlu.entitys.FileSecurityScanEntity();
            entity.setStorageScope(scope);
            entity.setRelativePath(path);
            entity.setOwnerUserId(owner);
            entity.setScanStatus(status.name());
            entity.setDiagnosticStatus(verdict == null ? null : verdict.name());
            entity.setSha256(sha256);
            entity.setDetail(detail);
            entity.setCompletedAt(status == FileSecurityStatus.PENDING ? null : new Date());
            // MySQL ON DUPLICATE KEY UPDATE commonly reports 2 affected rows
            // when an existing ledger row changes. Any positive count means
            // the verdict was durably written; zero/exception remains a
            // fail-closed persistence failure.
            return scanMapper.upsert(entity) > 0;
        } catch (RuntimeException error) {
            // Missing patch or transient DB outage must never grant publication.
            log.warn("写入文件扫描记录失败，继续按 fail-closed 处理: {}", path);
            return false;
        }
    }

    private void audit(String scope, String path, FileSecurityStatus status,
                       MalwareScanner.ScanVerdict verdict) {
        if (auditEventService == null) return;
        try {
            auditEventService.record(null, "FILE_SECURITY_SCAN", scope, path,
                    status == FileSecurityStatus.CLEAN ? "SUCCESS" : "FAILED",
                    verdict == null ? "UNKNOWN" : verdict.name());
        } catch (RuntimeException error) {
            log.warn("写入文件扫描审计失败: {}", path);
        }
    }

    private FileSecurityStatus publicationStatus(MalwareScanner.ScanVerdict verdict) {
        if (verdict == MalwareScanner.ScanVerdict.CLEAN) return FileSecurityStatus.CLEAN;
        if (verdict == MalwareScanner.ScanVerdict.INFECTED) return FileSecurityStatus.INFECTED;
        return FileSecurityStatus.PENDING;
    }

    private String safePath(Path file) {
        return file == null ? "<missing>" : file.toAbsolutePath().normalize().toString();
    }

    private String normalizeKey(String path) {
        if (path == null) return "<missing>";
        try { return safePath(Paths.get(path)); }
        catch (RuntimeException ignored) { return path; }
    }

    private String normalizeScope(String scope) {
        if (scope == null || !scope.matches("[A-Za-z0-9_-]{1,32}")) return "UNKNOWN";
        return scope;
    }

    private String digestOrNull(Path file) {
        if (file == null || !Files.isRegularFile(file)) return null;
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest.digest()) result.append(String.format("%02x", value));
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException error) {
            return null;
        }
    }

    private FileSecurityStatus parseStatus(String value) {
        try { return FileSecurityStatus.valueOf(value); }
        catch (RuntimeException ignored) { return FileSecurityStatus.PENDING; }
    }

    private MalwareScanner.ScanVerdict parseVerdict(String value) {
        try { return MalwareScanner.ScanVerdict.valueOf(value); }
        catch (RuntimeException ignored) { return MalwareScanner.ScanVerdict.FAILED; }
    }

    private static ExecutorService newExecutor() {
        // Bound scanner concurrency so a slow/uncooperative remote adapter
        // cannot create one lingering thread per upload after a timeout.
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "ganlu-file-security-scan");
            thread.setDaemon(true);
            return thread;
        });
    }
}
