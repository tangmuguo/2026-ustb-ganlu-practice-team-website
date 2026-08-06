package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.entitys.PublicImageMigrationIssue;
import com.vihu.ganlu.entitys.PublicImageMigrationReport;
import com.vihu.ganlu.entitys.PublicImageReferenceEntity;
import com.vihu.ganlu.mappers.PublicImageMigrationMapper;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.MaterialPathPolicy;
import com.vihu.ganlu.utils.PublicImagePathPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class PublicImageMigrationService {
    private final PublicImageMigrationMapper migrationMapper;
    private final PublicImageQuotaMapper quotaMapper;
    private final FileStorageUtil fileStorageUtil;
    private final boolean migrationEnabled;

    public PublicImageMigrationService(
            PublicImageMigrationMapper migrationMapper,
            PublicImageQuotaMapper quotaMapper,
            FileStorageUtil fileStorageUtil,
            @Value("${team.public-image.migration-enabled:false}") boolean migrationEnabled) {
        this.migrationMapper = migrationMapper;
        this.quotaMapper = quotaMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.migrationEnabled = migrationEnabled;
    }

    public PublicImageMigrationReport preflight() {
        return scan(null);
    }

    @Transactional
    public PublicImageMigrationReport migrate() {
        if (!migrationEnabled) {
            throw new IllegalStateException(
                    "公共图片迁移开关未开启；请先进入维护窗口并设置 TEAM_PUBLIC_IMAGE_MIGRATION_ENABLED=true");
        }
        List<PublicImageAssetEntity> lockedAssets = quotaMapper.findAllAssetsForUpdate();
        ScanPlan plan = buildPlan(lockedAssets == null ? Collections.emptyList() : lockedAssets);
        if (!plan.report.isMigrationAllowed()) {
            throw new MigrationBlockedException("公共图片迁移预检存在阻断项", plan.report);
        }

        for (AssetPlan candidate : plan.candidates) {
            PublicImageAssetEntity asset = new PublicImageAssetEntity();
            asset.setRelativePath(candidate.path);
            asset.setOwnerUserId(candidate.ownerUserId);
            asset.setFileSize(candidate.fileSize);
            if (quotaMapper.insertAsset(asset) != 1) {
                throw new IllegalStateException("迁移公共图片资产失败: " + candidate.path);
            }
        }
        for (AssetPlan repair : plan.repairs) {
            if (quotaMapper.updateAssetMetadata(
                    repair.assetId, repair.ownerUserId, repair.fileSize) != 1) {
                throw new IllegalStateException("修复公共图片资产失败: " + repair.path);
            }
        }
        quotaMapper.deleteAllQuotaRows();
        quotaMapper.rebuildQuotaRows();

        PublicImageMigrationReport verified = scan(null);
        verified.setMigratedCount(plan.candidates.size());
        verified.setRepairedCount(plan.repairs.size());
        if (!verified.isConsistent()) {
            throw new MigrationBlockedException("迁移后公共图片一致性断言失败，事务已回滚", verified);
        }
        return verified;
    }

    private PublicImageMigrationReport scan(List<PublicImageAssetEntity> knownAssets) {
        List<PublicImageAssetEntity> assets = knownAssets == null
                ? quotaMapper.findAllAssets() : knownAssets;
        return buildPlan(assets == null ? Collections.emptyList() : assets).report;
    }

    private ScanPlan buildPlan(List<PublicImageAssetEntity> assets) {
        ScanPlan plan = new ScanPlan();
        PublicImageMigrationReport report = plan.report;
        List<PublicImageReferenceEntity> references = migrationMapper.findBusinessReferences();
        if (references == null) references = Collections.emptyList();
        List<PublicImageReferenceEntity> courseCoverReferences = migrationMapper.findCourseCoverReferences();
        if (courseCoverReferences == null) courseCoverReferences = Collections.emptyList();
        List<PublicImageReferenceEntity> materialFileReferences = migrationMapper.findMaterialFileReferences();
        if (materialFileReferences == null) materialFileReferences = Collections.emptyList();
        report.setReferenceCount(references.size());
        report.setCourseCoverReferenceCount(courseCoverReferences.size());
        report.setMaterialFileReferenceCount(materialFileReferences.size());
        report.setRegisteredAssetCount(assets.size());

        Map<String, List<PublicImageReferenceEntity>> materialReferencesByPath = new LinkedHashMap<>();
        for (PublicImageReferenceEntity reference : materialFileReferences) {
            String normalized = MaterialPathPolicy.normalizeLocalPath(reference.getRelativePath());
            if (normalized != null) {
                materialReferencesByPath.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(reference);
            }
        }
        for (Map.Entry<String, List<PublicImageReferenceEntity>> entry : materialReferencesByPath.entrySet()) {
            long distinctCourses = entry.getValue().stream()
                    .map(PublicImageReferenceEntity::getSourceId).distinct().count();
            if (distinctCourses > 1) {
                report.setSharedMaterialPathCount(report.getSharedMaterialPathCount() + 1);
                String sources = entry.getValue().stream().map(this::source).collect(Collectors.joining(", "));
                issue(report, "SHARED_MATERIAL_FILE_PATH", sources, entry.getKey(),
                        "多个有效课件共享同一物理文件；上线前必须复制为独立文件并更新各自路径");
            }
        }

        Set<String> protectedCourseCoverPaths = new LinkedHashSet<>();
        for (PublicImageReferenceEntity reference : courseCoverReferences) {
            if (PublicImagePathPolicy.isExternalUrl(reference.getRelativePath())) continue;
            String normalized = PublicImagePathPolicy.normalizeCourseCoverReference(reference.getRelativePath());
            if (normalized != null) protectedCourseCoverPaths.add(normalized);
        }

        Map<String, PublicImageAssetEntity> assetsByPath = new LinkedHashMap<>();
        for (PublicImageAssetEntity asset : assets) {
            if (asset.getFileSize() != null && asset.getFileSize() > 0) {
                report.setRegisteredAssetBytes(
                        safeByteSum(report.getRegisteredAssetBytes(), asset.getFileSize()));
            }
            String normalized = PublicImagePathPolicy.normalizeManagedPath(asset.getRelativePath());
            if (normalized == null) {
                issue(report, "INVALID_ASSET_PATH", assetSource(asset), asset.getRelativePath(),
                        "账本路径不属于统一公共图片目录");
                continue;
            }
            assetsByPath.put(normalized, asset);
        }

        Map<String, List<PublicImageReferenceEntity>> referencesByPath = new LinkedHashMap<>();
        for (PublicImageReferenceEntity reference : references) {
            String raw = reference.getRelativePath();
            if (PublicImagePathPolicy.isExternalUrl(raw)) {
                report.setExternalReferenceCount(report.getExternalReferenceCount() + 1);
                continue;
            }
            String normalized = PublicImagePathPolicy.normalizeManagedPath(raw);
            if (normalized == null) {
                issue(report, "UNSUPPORTED_LOCAL_PATH", source(reference), raw,
                        "本地图片路径不兼容统一生命周期；请先人工替换或迁移");
                continue;
            }
            report.setManagedReferenceCount(report.getManagedReferenceCount() + 1);
            referencesByPath.computeIfAbsent(normalized, ignored -> new ArrayList<>()).add(reference);
        }

        for (PublicImageReferenceEntity reference : materialFileReferences) {
            String normalized = PublicImagePathPolicy.normalizeManagedPath(reference.getRelativePath());
            if (normalized != null && referencesByPath.containsKey(normalized)) {
                issue(report, "CROSS_DOMAIN_SHARED_PATH", source(reference), normalized,
                        "课件文件与公共图片业务共享同一物理文件；必须先复制为独立文件");
            }
        }
        protectedCourseCoverPaths.removeAll(referencesByPath.keySet());

        for (Map.Entry<String, List<PublicImageReferenceEntity>> entry : referencesByPath.entrySet()) {
            String path = entry.getKey();
            List<PublicImageReferenceEntity> pathReferences = entry.getValue();
            if (pathReferences.size() != 1) {
                String sources = pathReferences.stream().map(this::source).collect(Collectors.joining(", "));
                issue(report, "SHARED_PATH", sources, path,
                        "同一物理文件被多条业务记录引用；迁移前必须复制为独立文件或人工替换");
                continue;
            }
            PublicImageReferenceEntity reference = pathReferences.get(0);
            PublicImageAssetEntity existing = assetsByPath.get(path);
            Integer owner = resolveOwner(path, reference, existing);
            if (owner == null || owner <= 0) {
                issue(report, "UNKNOWN_OWNER", source(reference), path,
                        "无法可靠确定上传所有者；请人工补齐带账号目录的独立图片");
                continue;
            }

            Long actualSize = inspectFile(report, source(reference), path);
            if (actualSize == null) continue;
            report.setVerifiedReferenceBytes(
                    safeByteSum(report.getVerifiedReferenceBytes(), actualSize));
            if (existing == null) {
                plan.candidates.add(new AssetPlan(null, path, owner, actualSize));
            } else if (!owner.equals(existing.getOwnerUserId())) {
                issue(report, "OWNER_MISMATCH", source(reference), path,
                        "路径所有者与现有资产账本不一致");
            } else if (existing.getFileSize() == null
                    || existing.getFileSize().longValue() != actualSize.longValue()) {
                plan.repairs.add(new AssetPlan(existing.getAssetId(), path, owner, actualSize));
            }
        }

        for (Map.Entry<String, PublicImageAssetEntity> entry : assetsByPath.entrySet()) {
            if (!referencesByPath.containsKey(entry.getKey())) {
                issue(report, "ORPHAN_ASSET", assetSource(entry.getValue()), entry.getKey(),
                        "资产账本没有对应业务引用；请先确认后清理或恢复引用");
            }
        }

        List<String> diskFiles = managedDiskFiles(report, protectedCourseCoverPaths);
        report.setDiskFileCount(diskFiles.size());
        for (String diskFile : diskFiles) {
            if (!referencesByPath.containsKey(diskFile)) {
                issue(report, "ORPHAN_FILE", "DISK", diskFile,
                        "统一图片目录中的文件没有业务引用；请先确认后清理");
            }
        }

        report.setCandidateCount(plan.candidates.size());
        report.setRepairCount(plan.repairs.size());
        report.setMigrationAllowed(report.getIssues().isEmpty());
        report.setConsistent(report.isMigrationAllowed()
                && plan.candidates.isEmpty() && plan.repairs.isEmpty()
                && report.getManagedReferenceCount() == report.getRegisteredAssetCount()
                && report.getManagedReferenceCount() == report.getDiskFileCount()
                && report.getVerifiedReferenceBytes() == report.getRegisteredAssetBytes()
                && report.getVerifiedReferenceBytes() == report.getDiskBytes());
        return plan;
    }

    private Integer resolveOwner(
            String path, PublicImageReferenceEntity reference, PublicImageAssetEntity existing) {
        Integer owner = PublicImagePathPolicy.ownerFromPath(path);
        if (owner != null) return owner;
        if (existing != null && existing.getOwnerUserId() != null && existing.getOwnerUserId() > 0) {
            return existing.getOwnerUserId();
        }
        return reference.getOwnerHint() != null && reference.getOwnerHint() > 0
                ? reference.getOwnerHint() : null;
    }

    private Long inspectFile(PublicImageMigrationReport report, String source, String relativePath) {
        try {
            Path file = fileStorageUtil.loadFile(relativePath);
            if (!Files.isRegularFile(file)) {
                issue(report, "MISSING_FILE", source, relativePath, "业务图片对应的物理文件不存在");
                return null;
            }
            Path root = fileStorageUtil.getUploadRoot().toAbsolutePath().normalize();
            Path normalizedFile = file.toAbsolutePath().normalize();
            if (!normalizedFile.startsWith(root) || containsSymbolicLink(root, normalizedFile)) {
                issue(report, "OUTSIDE_UPLOAD_ROOT", source, relativePath, "文件通过链接越出了上传根目录");
                return null;
            }
            long size = Files.size(file);
            if (size <= 0) {
                issue(report, "EMPTY_FILE", source, relativePath, "物理文件为空，不能按 0 字节迁移");
                return null;
            }
            return size;
        } catch (IOException | RuntimeException error) {
            issue(report, "FILE_INSPECTION_FAILED", source, relativePath,
                    "读取物理文件失败: " + safeMessage(error));
            return null;
        }
    }

    private boolean containsSymbolicLink(Path root, Path file) {
        Path current = root;
        for (Path part : root.relativize(file)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    private List<String> managedDiskFiles(
            PublicImageMigrationReport report, Set<String> protectedCourseCoverPaths) {
        List<String> files = new ArrayList<>();
        for (String rootName : new String[]{"images", "images_pending"}) {
            Path root = fileStorageUtil.getUploadRoot().resolve(rootName).normalize();
            if (!Files.exists(root)) continue;
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .sorted()
                        .forEach(file -> {
                            String path = fileStorageUtil.toRelativePath(file);
                            if (PublicImagePathPolicy.isCourseCoverNamespace(path)
                                    || protectedCourseCoverPaths.contains(path)) {
                                report.setExcludedCourseCoverFileCount(
                                        report.getExcludedCourseCoverFileCount() + 1);
                                try {
                                    report.setExcludedCourseCoverBytes(safeByteSum(
                                            report.getExcludedCourseCoverBytes(), Files.size(file)));
                                } catch (IOException error) {
                                    issue(report, "FILE_INSPECTION_FAILED", "COURSE_COVER", path,
                                            "读取课件封面文件大小失败: " + safeMessage(error));
                                }
                                return;
                            }
                            String normalized = PublicImagePathPolicy.normalizeManagedPath(path);
                            if (normalized == null) {
                                issue(report, "UNSUPPORTED_DISK_FILE", "DISK", path,
                                        "统一图片目录存在非标准文件，迁移前必须人工确认");
                            } else {
                                files.add(normalized);
                                try {
                                    report.setDiskBytes(safeByteSum(report.getDiskBytes(), Files.size(file)));
                                } catch (IOException error) {
                                    issue(report, "FILE_INSPECTION_FAILED", "DISK", path,
                                            "读取磁盘文件大小失败: " + safeMessage(error));
                                }
                            }
                        });
            } catch (IOException error) {
                issue(report, "DISK_SCAN_FAILED", "DISK", rootName,
                        "扫描图片目录失败: " + safeMessage(error));
            }
        }
        return files;
    }

    private void issue(PublicImageMigrationReport report, String code, String source, String path, String message) {
        report.getIssues().add(new PublicImageMigrationIssue(code, source, path, message));
    }

    private String source(PublicImageReferenceEntity reference) {
        return reference.getSourceType() + "#" + reference.getSourceId();
    }

    private String assetSource(PublicImageAssetEntity asset) {
        return "ASSET#" + asset.getAssetId();
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private long safeByteSum(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new IllegalStateException("公共图片字节汇总溢出，禁止迁移");
        }
        return left + right;
    }

    private static final class ScanPlan {
        private final PublicImageMigrationReport report = new PublicImageMigrationReport();
        private final List<AssetPlan> candidates = new ArrayList<>();
        private final List<AssetPlan> repairs = new ArrayList<>();
    }

    private static final class AssetPlan {
        private final Long assetId;
        private final String path;
        private final int ownerUserId;
        private final long fileSize;

        private AssetPlan(Long assetId, String path, int ownerUserId, long fileSize) {
            this.assetId = assetId;
            this.path = path;
            this.ownerUserId = ownerUserId;
            this.fileSize = fileSize;
        }
    }

    public static class MigrationBlockedException extends IllegalStateException {
        private final PublicImageMigrationReport report;

        public MigrationBlockedException(String message, PublicImageMigrationReport report) {
            super(message);
            this.report = report;
        }

        public PublicImageMigrationReport getReport() {
            return report;
        }
    }
}
