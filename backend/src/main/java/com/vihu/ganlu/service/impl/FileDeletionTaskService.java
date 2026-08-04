package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.mappers.FileDeletionTaskMapper;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
public class FileDeletionTaskService {
    private final FileDeletionTaskMapper taskMapper;
    private final PublicImageQuotaMapper imageQuotaMapper;
    private final FileDeletionTaskProcessor processor;
    private final FileDeletionTaskFailureService failureService;

    public FileDeletionTaskService(
            FileDeletionTaskMapper taskMapper,
            PublicImageQuotaMapper imageQuotaMapper,
            FileDeletionTaskProcessor processor,
            FileDeletionTaskFailureService failureService) {
        this.taskMapper = taskMapper;
        this.imageQuotaMapper = imageQuotaMapper;
        this.processor = processor;
        this.failureService = failureService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueuePublicImage(String relativePath) {
        PublicImageAssetEntity asset = imageQuotaMapper.findAssetForUpdate(relativePath);
        if (asset == null || asset.getAssetId() == null) {
            throw new IllegalStateException("公共图片资产账本缺失，拒绝删除业务记录");
        }
        FileDeletionTaskEntity task = new FileDeletionTaskEntity();
        task.setAssetType(FileDeletionTaskProcessor.PUBLIC_IMAGE);
        task.setAssetId(asset.getAssetId());
        task.setRelativePath(asset.getRelativePath());
        task.setOwnerUserId(asset.getOwnerUserId());
        task.setFileSize(asset.getFileSize());
        insertAndRunAfterCommit(task);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueTeamMedia(TeamMediaEntity media) {
        if (media == null || media.getId() == null || media.getUploaderId() == null || media.getFileSize() == null) {
            throw new IllegalArgumentException("附件删除任务数据不完整");
        }
        FileDeletionTaskEntity task = new FileDeletionTaskEntity();
        task.setAssetType(FileDeletionTaskProcessor.TEAM_MEDIA);
        task.setAssetId(media.getId().longValue());
        task.setRelativePath(media.getRelativePath());
        task.setOwnerUserId(media.getUploaderId());
        task.setFileSize(media.getFileSize());
        insertAndRunAfterCommit(task);
    }

    public List<FileDeletionTaskEntity> listTasks(int limit) {
        return taskMapper.findAll(Math.max(1, Math.min(limit, 200)));
    }

    public boolean retryNow(long taskId) {
        FileDeletionTaskEntity task = taskMapper.findById(taskId);
        return task == null || processSafely(task);
    }

    @Scheduled(fixedDelayString = "${file-deletion.retry-interval-ms:60000}")
    public void retryPendingTasks() {
        for (FileDeletionTaskEntity task : taskMapper.findRetryable(50)) {
            processSafely(task);
        }
    }

    private void insertAndRunAfterCommit(FileDeletionTaskEntity task) {
        taskMapper.insertTask(task);
        if (task.getId() == null) {
            FileDeletionTaskEntity existing = taskMapper.findByAsset(task.getAssetType(), task.getAssetId());
            if (existing != null) task.setId(existing.getId());
        }
        if (task.getId() == null) {
            throw new IllegalStateException("创建持久化删除任务失败");
        }
        long taskId = task.getId();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("删除任务必须在业务事务中创建");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    retryNow(taskId);
                } catch (RuntimeException error) {
                    // 任务已随业务事务持久化；数据库短暂不可用时由定时扫描再次处理。
                    log.error("提交后立即执行删除任务失败，等待定时重试: taskId={}", taskId, error);
                }
            }
        });
    }

    private boolean processSafely(FileDeletionTaskEntity task) {
        try {
            return processor.process(task.getId());
        } catch (RuntimeException error) {
            failureService.recordFailure(task.getId(), error.getMessage(),
                    task.getRetryCount() == null ? 0 : task.getRetryCount());
            int failures = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            if (failures >= 5) {
                log.error("文件删除任务已连续失败 {} 次，需要管理员处理: taskId={}, type={}, assetId={}",
                        failures, task.getId(), task.getAssetType(), task.getAssetId(), error);
            } else {
                log.warn("文件删除任务失败，已安排重试: taskId={}, type={}, assetId={}",
                        task.getId(), task.getAssetType(), task.getAssetId(), error);
            }
            return false;
        }
    }
}
