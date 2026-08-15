package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamMediaQuotaMapper;
import com.vihu.ganlu.mappers.TeamPageImageMapper;
import com.vihu.ganlu.mappers.TeamPageWordMapper;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.service.TeamMediaService;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.security.file.ChildPrivacyGateService;
import com.vihu.ganlu.security.file.FileScanResult;
import com.vihu.ganlu.security.file.FileScanService;
import com.vihu.ganlu.security.file.PrivacyAssetType;
import com.vihu.ganlu.security.file.QuarantineStorageService;
import com.vihu.ganlu.security.file.QuarantinedFile;
import com.vihu.ganlu.security.file.MalwareScanner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class TeamMediaServiceImpl implements TeamMediaService {
    private final TeamMediaMapper teamMediaMapper;
    private final TeamMediaQuotaMapper quotaMapper;
    private final TeamPageImageMapper teamPageImageMapper;
    private final TeamPageWordMapper teamPageWordMapper;
    private final FileStorageUtil fileStorageUtil;
    private final TeamMediaCapacityService capacityService;
    private final FileDeletionTaskService deletionTaskService;
    private final FileScanService fileScanService;
    private final QuarantineStorageService quarantineStorageService;
    private final ChildPrivacyGateService childPrivacyGateService;
    private final boolean secureUploadFlow;
    private final int ownerMaxFiles;
    private final long ownerMaxBytes;
    private final int globalMaxFiles;
    private final long globalMaxBytes;

    /** Legacy constructor retained for storage/quota unit tests. */
    public TeamMediaServiceImpl(
            TeamMediaMapper teamMediaMapper,
            TeamMediaQuotaMapper quotaMapper,
            TeamPageImageMapper teamPageImageMapper,
            TeamPageWordMapper teamPageWordMapper,
            FileStorageUtil fileStorageUtil,
            TeamMediaCapacityService capacityService,
            FileDeletionTaskService deletionTaskService,
            @Value("${team.media.owner-max-files:50}") int ownerMaxFiles,
            @Value("${team.media.owner-max-total-mb:2048}") long ownerMaxTotalMb,
            @Value("${team.media.global-max-files:2000}") int globalMaxFiles,
            @Value("${team.media.global-max-total-mb:20480}") long globalMaxTotalMb) {
        this(teamMediaMapper, quotaMapper, teamPageImageMapper, teamPageWordMapper, fileStorageUtil,
                capacityService, deletionTaskService,
                new FileScanService(path -> MalwareScanner.ScanVerdict.CLEAN, 5000), null,
                new ChildPrivacyGateService(null),
                ownerMaxFiles, ownerMaxTotalMb, globalMaxFiles, globalMaxTotalMb);
    }

    @Autowired
    public TeamMediaServiceImpl(
            TeamMediaMapper teamMediaMapper,
            TeamMediaQuotaMapper quotaMapper,
            TeamPageImageMapper teamPageImageMapper,
            TeamPageWordMapper teamPageWordMapper,
            FileStorageUtil fileStorageUtil,
            TeamMediaCapacityService capacityService,
            FileDeletionTaskService deletionTaskService,
            FileScanService fileScanService,
            QuarantineStorageService quarantineStorageService,
            ChildPrivacyGateService childPrivacyGateService,
            @Value("${team.media.owner-max-files:50}") int ownerMaxFiles,
            @Value("${team.media.owner-max-total-mb:2048}") long ownerMaxTotalMb,
            @Value("${team.media.global-max-files:2000}") int globalMaxFiles,
            @Value("${team.media.global-max-total-mb:20480}") long globalMaxTotalMb) {
        this.teamMediaMapper = teamMediaMapper;
        this.quotaMapper = quotaMapper;
        this.teamPageImageMapper = teamPageImageMapper;
        this.teamPageWordMapper = teamPageWordMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.capacityService = capacityService;
        this.deletionTaskService = deletionTaskService;
        this.fileScanService = fileScanService;
        this.quarantineStorageService = quarantineStorageService;
        this.childPrivacyGateService = childPrivacyGateService;
        this.secureUploadFlow = quarantineStorageService != null && fileScanService != null;
        this.ownerMaxFiles = Math.max(1, ownerMaxFiles);
        this.ownerMaxBytes = Math.max(200L, ownerMaxTotalMb) * 1024L * 1024L;
        this.globalMaxFiles = Math.max(this.ownerMaxFiles, globalMaxFiles);
        this.globalMaxBytes = Math.max(this.ownerMaxBytes, globalMaxTotalMb * 1024L * 1024L);
    }

    @Override
    @Transactional
    public TeamMediaEntity uploadMedia(MultipartFile file, int uploaderId, int teamId,
                                       String relatedType, Integer relatedId) {
        FileStorageUtil.ValidatedFile validated = fileStorageUtil.validate(file, FileStorageUtil.MAX_VIDEO_SIZE);
        long fileSize = validated.getSize();
        lockAndValidateParent(relatedType, relatedId, teamId);
        capacityService.ensureFormalCapacity(fileSize);
        reserveQuota(uploaderId, fileSize);

        String relativePath;
        FileScanResult scanResult = null;
        if (secureUploadFlow) {
            QuarantinedFile quarantined = quarantineStorageService.stage(
                    file, "TEAM_MEDIA", uploaderId, validated.getExtension());
            scanResult = quarantineStorageService.scan(quarantined);
            if (scanResult.isClean()) {
                relativePath = quarantineStorageService.promoteIfClean(quarantined, "protected/media");
            } else {
                // PENDING/INFECTED files remain below quarantine and can never
                // be served by the public/download gates.
                relativePath = quarantined.getQuarantinePath();
            }
        } else {
            relativePath = fileStorageUtil.storeFile(file, "media/" + uploaderId, validated.getExtension());
        }
        registerRollbackCleanup(relativePath);
        TeamMediaEntity entity = new TeamMediaEntity();
        entity.setFilename(FileStorageUtil.safeLeafName(file.getOriginalFilename()));
        entity.setRelativePath(relativePath);
        entity.setMimeType(validated.getMimeType());
        entity.setFileSize(fileSize);
        entity.setUploaderId(uploaderId);
        entity.setTeamId(teamId);
        entity.setRelatedType(relatedType);
        entity.setRelatedId(relatedId);
        entity.setStatus("PENDING");
        if (scanResult != null) {
            entity.setScanStatus(scanResult.getStatus().name());
            entity.setScanDiagnosticStatus(scanResult.getDiagnosticVerdict().name());
        }
        if (teamMediaMapper.insertTeamMedia(entity) != 1) {
            throw new IllegalStateException("保存附件记录失败");
        }
        return entity;
    }

    @Override
    public TeamMediaEntity findById(int id) {
        return teamMediaMapper.findById(id);
    }

    @Override
    public List<TeamMediaEntity> findByTeamId(int teamId) {
        return teamMediaMapper.findByTeamId(teamId);
    }

    @Override
    public List<TeamMediaEntity> findByStatus(int teamId, String status) {
        return teamMediaMapper.findByStatus(teamId, status);
    }

    @Override
    public List<TeamMediaEntity> findPublicByTeamId(int teamId) {
        return teamMediaMapper.findPublicByTeamId(teamId);
    }

    @Override
    @Transactional
    public boolean updateStatus(int id, String status, String rejectReason) {
        TeamMediaEntity existing = teamMediaMapper.findByIdForUpdate(id);
        if (existing == null) return false;
        if (secureUploadFlow && "PUBLISHED".equals(status)) {
            if (!"CLEAN".equals(existing.getScanStatus())
                    || existing.getRelativePath() == null
                    || !fileScanService.isClean(fileStorageUtil.loadFile(existing.getRelativePath()))) {
                throw new com.vihu.ganlu.security.file.FileSecurityException(
                        "附件尚未通过安全扫描，禁止公开或下载");
            }
            PrivacyAssetType type = privacyType(existing.getRelatedType());
            if (type != null) {
                childPrivacyGateService.requirePublicationAllowed(type,
                        existing.getId() == null ? null : existing.getId().longValue(),
                        existing.getUploaderId(), null);
            }
        }
        if (teamMediaMapper.updateStatus(id, status, rejectReason) != 1) {
            throw new IllegalStateException("更新附件状态失败");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean archiveByRelated(String relatedType, int relatedId, int teamId) {
        return teamMediaMapper.archiveByRelated(relatedType, relatedId, teamId) > 0;
    }

    @Override
    @Transactional
    public int deleteByIds(List<Integer> ids) {
        return teamMediaMapper.deleteByIds(ids);
    }

    @Override
    @Transactional
    public int deleteByIdsAndUploader(List<Integer> ids, int uploaderId) {
        return teamMediaMapper.deleteByIdsAndUploader(ids, uploaderId);
    }

    @Override
    @Transactional
    public boolean archiveByIdAndTeamId(int id, int teamId) {
        TeamMediaEntity existing = teamMediaMapper.findByIdForUpdate(id);
        if (existing == null || !Integer.valueOf(teamId).equals(existing.getTeamId())) return false;
        if (teamMediaMapper.archiveByIdAndTeamId(id, teamId) != 1) {
            throw new IllegalStateException("归档附件失败");
        }
        return true;
    }

    @Override
    @Transactional
    public boolean purgeById(int id) {
        TeamMediaEntity existing = teamMediaMapper.findByIdForUpdate(id);
        if (existing == null) return false;
        if (!"ARCHIVED".equals(existing.getStatus())) {
            throw new IllegalStateException("附件必须先归档，才能彻底清理");
        }
        deletionTaskService.enqueueTeamMedia(existing);
        return true;
    }

    private void reserveQuota(int uploaderId, long fileSize) {
        if (uploaderId <= 0) throw new IllegalArgumentException("附件上传用户不正确");
        quotaMapper.ensureGlobalQuotaRow();
        if (quotaMapper.reserveGlobalQuota(fileSize, globalMaxFiles, globalMaxBytes) != 1) {
            throw new IllegalStateException("服务器附件总量已达到上限，请联系管理员清理归档附件");
        }
        quotaMapper.ensureOwnerQuotaRow(uploaderId);
        if (quotaMapper.reserveOwnerQuota(uploaderId, fileSize, ownerMaxFiles, ownerMaxBytes) != 1) {
            throw new IllegalStateException("当前账号附件数量或容量已达到上限，请先归档并申请清理");
        }
    }

    private void lockAndValidateParent(String relatedType, Integer relatedId, int teamId) {
        if ((relatedType == null) != (relatedId == null)) {
            throw new IllegalArgumentException("relatedType 与 relatedId 必须同时提供或同时省略");
        }
        if (relatedType == null) return;
        if (relatedId <= 0) throw new IllegalArgumentException("关联内容编号不正确");
        if ("IMAGE".equals(relatedType)) {
            TeamPageImageEntity parent = teamPageImageMapper.findByIdForUpdate(relatedId);
            requireAvailableParent(parent == null ? null : parent.getTeamId(),
                    parent == null ? null : parent.getStatus(), teamId);
        } else if ("WORD".equals(relatedType)) {
            TeamPageWordEntity parent = teamPageWordMapper.findByIdForUpdate(relatedId);
            requireAvailableParent(parent == null ? null : parent.getTeamId(),
                    parent == null ? null : parent.getStatus(), teamId);
        } else {
            throw new IllegalArgumentException("无效的关联类型: " + relatedType);
        }
    }

    private void requireAvailableParent(Integer parentTeamId, String parentStatus, int teamId) {
        if (parentTeamId == null || !Integer.valueOf(teamId).equals(parentTeamId)
                || "ARCHIVED".equals(parentStatus)) {
            throw new IllegalArgumentException("关联的父内容不存在、不属于当前团队或已归档");
        }
    }

    private void registerRollbackCleanup(String relativePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        fileStorageUtil.deleteFile(relativePath);
                    } catch (RuntimeException ignored) {
                        // 保留原始事务失败；运维对账会发现无业务引用文件。
                    }
                }
            }
        });
    }

    private PrivacyAssetType privacyType(String relatedType) {
        if ("VIDEO".equalsIgnoreCase(relatedType)
                || "CHILD_VIDEO".equalsIgnoreCase(relatedType)) {
            return PrivacyAssetType.CHILD_VIDEO;
        }
        if ("CLASSROOM_LOG".equalsIgnoreCase(relatedType)
                || "LOG".equalsIgnoreCase(relatedType)) {
            return PrivacyAssetType.CLASSROOM_LOG;
        }
        return null;
    }
}
