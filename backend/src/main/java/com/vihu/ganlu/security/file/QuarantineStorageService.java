package com.vihu.ganlu.security.file;

import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Stores uploads below a non-public quarantine root and promotes only a CLEAN
 * file into a caller-owned controlled root.
 */
@Service
public class QuarantineStorageService {
    private static final String QUARANTINE_ROOT = "quarantine";

    private final FileStorageUtil storage;
    private final FileScanService scanService;

    @Autowired
    public QuarantineStorageService(FileStorageUtil storage, FileScanService scanService) {
        this.storage = storage;
        this.scanService = scanService;
    }

    public QuarantinedFile stage(MultipartFile file, String scope, int ownerUserId, String extension) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("隔离上传文件不能为空");
        String safeScope = safeScope(scope);
        if (ownerUserId <= 0) throw new IllegalArgumentException("隔离上传用户不正确");
        String safeExtension = FileStorageUtil.extensionOf("x." + extension);
        String path = storage.storeFile(file,
                QUARANTINE_ROOT + "/" + safeScope + "/" + ownerUserId, safeExtension);
        return new QuarantinedFile(safeScope, ownerUserId, safeExtension,
                FileStorageUtil.safeLeafName(file.getOriginalFilename()), path);
    }

    public FileScanResult scan(QuarantinedFile file) {
        if (file == null) throw new IllegalArgumentException("隔离文件不能为空");
        FileScanResult result = scanService.scan(storage.loadFile(file.getQuarantinePath()),
                file.getScope(), file.getOwnerUserId());
        file.setScanResult(result);
        return result;
    }

    /**
     * Move a quarantined file only after a fresh/known CLEAN result.  A scan
     * record is moved with it; if the record cannot be moved the operation
     * fails closed and the source remains in quarantine when possible.
     */
    public String promoteIfClean(QuarantinedFile file, String controlledRoot) {
        if (file == null || file.getScanResult() == null || !file.getScanResult().isClean()) {
            throw new FileSecurityException("文件尚未通过安全扫描，禁止迁入正式目录",
                    file == null ? null : file.getScanResult());
        }
        Path source = storage.loadFile(file.getQuarantinePath());
        scanService.requireClean(source);
        String target = storage.allocatePath(
                safeRoot(controlledRoot) + "/" + file.getOwnerUserId(), file.getExtension());
        Path targetFile = storage.loadFile(target);
        // Persist the clean-path transition before the physical move. If the
        // database update cannot be made, no controlled file is created.
        if (!scanService.moveRecord(source.toString(), targetFile.toString())) {
            throw new FileSecurityException("文件安全记录无法迁移，禁止进入正式目录");
        }
        try {
            storage.moveFile(file.getQuarantinePath(), target);
        } catch (RuntimeException error) {
            // A failed physical move leaves the source in quarantine. Restore
            // the ledger path so a retry cannot point at a nonexistent target.
            scanService.moveRecord(targetFile.toString(), source.toString());
            throw error;
        }
        file.setControlledPath(target);
        return target;
    }

    public Path loadControlled(QuarantinedFile file) {
        if (file == null || file.getControlledPath() == null) {
            throw new FileSecurityException("文件尚未迁入受控目录");
        }
        Path path = storage.loadFile(file.getControlledPath());
        scanService.requireClean(path);
        return path;
    }

    private String safeScope(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("隔离文件类型不合法");
        }
        return value;
    }

    private String safeRoot(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_/-]{1,120}")
                || value.startsWith("/") || value.contains("..")) {
            throw new IllegalArgumentException("受控文件目录不合法");
        }
        return value;
    }
}
