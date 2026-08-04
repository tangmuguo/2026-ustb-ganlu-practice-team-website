package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.mappers.FileDeletionTaskMapper;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamMediaQuotaMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;

@Service
public class FileDeletionTaskProcessor {
    public static final String PUBLIC_IMAGE = "PUBLIC_IMAGE";
    public static final String TEAM_MEDIA = "TEAM_MEDIA";

    private final FileDeletionTaskMapper taskMapper;
    private final PublicImageAssetDeletionService imageDeletionService;
    private final TeamMediaMapper mediaMapper;
    private final TeamMediaQuotaMapper mediaQuotaMapper;
    private final FileStorageUtil fileStorageUtil;

    public FileDeletionTaskProcessor(
            FileDeletionTaskMapper taskMapper,
            PublicImageAssetDeletionService imageDeletionService,
            TeamMediaMapper mediaMapper,
            TeamMediaQuotaMapper mediaQuotaMapper,
            FileStorageUtil fileStorageUtil) {
        this.taskMapper = taskMapper;
        this.imageDeletionService = imageDeletionService;
        this.mediaMapper = mediaMapper;
        this.mediaQuotaMapper = mediaQuotaMapper;
        this.fileStorageUtil = fileStorageUtil;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(long taskId) {
        FileDeletionTaskEntity task = taskMapper.findByIdForUpdate(taskId);
        if (task == null) return true;
        if (PUBLIC_IMAGE.equals(task.getAssetType())) {
            imageDeletionService.deletePhysicalFileThenReleaseQuota(task.getAssetId());
        } else if (TEAM_MEDIA.equals(task.getAssetType())) {
            deleteTeamMedia(task);
        } else {
            throw new IllegalStateException("未知删除任务类型: " + task.getAssetType());
        }
        if (taskMapper.deleteTask(taskId) != 1) {
            throw new IllegalStateException("删除任务完成后无法清理任务记录");
        }
        return true;
    }

    private void deleteTeamMedia(FileDeletionTaskEntity task) {
        int mediaId = Math.toIntExact(task.getAssetId());
        TeamMediaEntity media = mediaMapper.findByIdForUpdate(mediaId);
        if (media != null && !"ARCHIVED".equals(media.getStatus())) {
            throw new IllegalStateException("附件尚未归档，禁止物理删除");
        }
        String path = media == null ? task.getRelativePath() : media.getRelativePath();
        deletePhysicalFileIdempotently(path);
        if (media != null && mediaMapper.purgeById(mediaId) != 1) {
            throw new IllegalStateException("彻底删除附件记录失败");
        }
        int ownerId = media != null && media.getUploaderId() != null
                ? media.getUploaderId() : task.getOwnerUserId();
        long fileSize = media != null && media.getFileSize() != null
                ? media.getFileSize() : task.getFileSize();
        if (ownerId <= 0 || fileSize < 0) throw new IllegalStateException("附件配额任务数据不完整");
        // 与上传保持相同锁顺序：全局行 → 账号行，降低并发上传/purge 的死锁概率。
        if (mediaQuotaMapper.releaseGlobalQuota(fileSize) != 1
                || mediaQuotaMapper.releaseOwnerQuota(ownerId, fileSize) != 1) {
            throw new IllegalStateException("释放附件配额失败");
        }
    }

    private void deletePhysicalFileIdempotently(String relativePath) {
        if (Files.exists(fileStorageUtil.loadFile(relativePath))) {
            fileStorageUtil.deleteFile(relativePath);
        }
        if (Files.exists(fileStorageUtil.loadFile(relativePath))) {
            throw new IllegalStateException("附件物理文件删除失败");
        }
    }
}
