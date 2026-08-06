package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.FileDeletionTaskEntity;
import com.vihu.ganlu.entitys.TeamMediaEntity;
import com.vihu.ganlu.entitys.CourseDetailEntity;
import com.vihu.ganlu.mappers.CourseDetailMapper;
import com.vihu.ganlu.mappers.FileDeletionTaskMapper;
import com.vihu.ganlu.mappers.TeamMediaMapper;
import com.vihu.ganlu.mappers.TeamMediaQuotaMapper;
import com.vihu.ganlu.utils.FileStorageUtil;
import com.vihu.ganlu.utils.MaterialPathPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;

@Service
public class FileDeletionTaskProcessor {
    public static final String PUBLIC_IMAGE = "PUBLIC_IMAGE";
    public static final String TEAM_MEDIA = "TEAM_MEDIA";
    public static final String COURSE_COVER = "COURSE_COVER";
    public static final String COURSE_ORIGINAL = "COURSE_ORIGINAL";
    public static final String COURSE_PREVIEW = "COURSE_PREVIEW";
    public static final String COURSE_ORPHAN = "COURSE_ORPHAN";

    private final FileDeletionTaskMapper taskMapper;
    private final PublicImageAssetDeletionService imageDeletionService;
    private final TeamMediaMapper mediaMapper;
    private final TeamMediaQuotaMapper mediaQuotaMapper;
    private final FileStorageUtil fileStorageUtil;
    private final CourseDetailMapper courseDetailMapper;

    public FileDeletionTaskProcessor(
            FileDeletionTaskMapper taskMapper,
            PublicImageAssetDeletionService imageDeletionService,
            TeamMediaMapper mediaMapper,
            TeamMediaQuotaMapper mediaQuotaMapper,
            FileStorageUtil fileStorageUtil,
            CourseDetailMapper courseDetailMapper) {
        this.taskMapper = taskMapper;
        this.imageDeletionService = imageDeletionService;
        this.mediaMapper = mediaMapper;
        this.mediaQuotaMapper = mediaQuotaMapper;
        this.fileStorageUtil = fileStorageUtil;
        this.courseDetailMapper = courseDetailMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(long taskId) {
        FileDeletionTaskEntity task = taskMapper.findByIdForUpdate(taskId);
        if (task == null) return true;
        if (PUBLIC_IMAGE.equals(task.getAssetType())) {
            imageDeletionService.deletePhysicalFileThenReleaseQuota(task.getAssetId());
        } else if (TEAM_MEDIA.equals(task.getAssetType())) {
            deleteTeamMedia(task);
        } else if (isCourseFileTask(task.getAssetType())) {
            deleteCourseFile(task);
        } else if (COURSE_ORPHAN.equals(task.getAssetType())) {
            deleteCourseOrphan(task);
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

    private boolean isCourseFileTask(String assetType) {
        return COURSE_COVER.equals(assetType)
                || COURSE_ORIGINAL.equals(assetType)
                || COURSE_PREVIEW.equals(assetType);
    }

    private void deleteCourseFile(FileDeletionTaskEntity task) {
        int courseId = Math.toIntExact(task.getAssetId());
        CourseDetailEntity course = courseDetailMapper.getCourseByIdIncludingDeletedForUpdate(courseId);
        if (course != null && Integer.valueOf(1).equals(course.getStatus())) {
            throw new IllegalStateException("课件尚未删除，禁止物理删除文件");
        }
        String currentPath = currentCoursePath(course, task.getAssetType());
        String normalized = MaterialPathPolicy.normalizeLocalPath(
                currentPath == null ? task.getRelativePath() : currentPath);
        if (normalized == null) return;
        if (courseDetailMapper.countActiveFileReferences(normalized, courseId) > 0) {
            return; // A historical shared file remains owned by another active course.
        }
        deletePhysicalFileIdempotently(normalized);
    }

    private void deleteCourseOrphan(FileDeletionTaskEntity task) {
        String normalized = MaterialPathPolicy.normalizeLocalPath(task.getRelativePath());
        if (normalized == null) return;
        if (courseDetailMapper.countActiveFileReferences(normalized, null) > 0) {
            return; // The outer create transaction committed; the file is no longer an orphan.
        }
        deletePhysicalFileIdempotently(normalized);
    }

    private String currentCoursePath(CourseDetailEntity course, String type) {
        if (course == null) return null;
        if (COURSE_COVER.equals(type)) return course.getThumbnailUrl();
        if (COURSE_ORIGINAL.equals(type)) return course.getOriginalFilePath();
        if (COURSE_PREVIEW.equals(type)) return course.getPreviewFilePath();
        return null;
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
