package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.entitys.PublicImageAssetEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.mappers.FileDeletionTaskMapper;
import com.vihu.ganlu.mappers.PublicImageQuotaMapper;
import com.vihu.ganlu.utils.MaterialPathPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueCourseFiles(CourseDetailEntity course) {
        if (course == null || course.getId() == null) {
            throw new IllegalArgumentException("课件删除任务数据不完整");
        }
        int ownerId = course.getUploaderUserId() == null ? 0 : course.getUploaderUserId();
        enqueueCourseFile(FileDeletionTaskProcessor.COURSE_COVER, course.getId(),
                course.getThumbnailUrl(), ownerId, 0L);
        enqueueCourseFile(FileDeletionTaskProcessor.COURSE_ORIGINAL, course.getId(),
                course.getOriginalFilePath(), ownerId,
                course.getFileSize() == null ? 0L : course.getFileSize());
        enqueueCourseFile(FileDeletionTaskProcessor.COURSE_PREVIEW, course.getId(),
                course.getPreviewFilePath(), ownerId, 0L);
    }

    /**
     * Persists recovery intent independently from the course transaction and before the permanent file is written.
     * The delay prevents a normal long-running transaction from racing the scanner; the processor also rechecks
     * active references, so a crash immediately after commit cannot delete a valid course file.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long enqueueCourseOrphanCleanup(String relativePath, int ownerId, long fileSize) {
        String normalizedPath = MaterialPathPolicy.normalizeLocalPath(relativePath);
        if (normalizedPath == null || fileSize < 0) {
            throw new IllegalArgumentException("课件孤儿清理任务数据不完整");
        }
        FileDeletionTaskEntity task = new FileDeletionTaskEntity();
        task.setAssetType(FileDeletionTaskProcessor.COURSE_ORPHAN);
        long assetId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        task.setAssetId(assetId == 0 ? 1 : assetId);
        task.setRelativePath(normalizedPath);
        task.setOwnerUserId(Math.max(0, ownerId));
        task.setFileSize(fileSize);
        task.setNextRetryAt(Timestamp.from(Instant.now().plus(30, ChronoUnit.MINUTES)));
        taskMapper.insertTask(task);
        if (task.getId() == null) {
            FileDeletionTaskEntity existing = taskMapper.findByAsset(task.getAssetType(), task.getAssetId());
            if (existing != null) task.setId(existing.getId());
        }
        if (task.getId() == null) throw new IllegalStateException("创建课件孤儿清理任务失败");
        return task.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelTask(long taskId) {
        taskMapper.deleteTask(taskId);
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

    private void enqueueCourseFile(String type, int courseId, String relativePath, int ownerId, long fileSize) {
        String normalizedPath = MaterialPathPolicy.normalizeLocalPath(relativePath);
        if (normalizedPath == null) return;
        FileDeletionTaskEntity task = new FileDeletionTaskEntity();
        task.setAssetType(type);
        task.setAssetId((long) courseId);
        task.setRelativePath(normalizedPath);
        task.setOwnerUserId(Math.max(0, ownerId));
        task.setFileSize(Math.max(0L, fileSize));
        insertAndRunAfterCommit(task);
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
